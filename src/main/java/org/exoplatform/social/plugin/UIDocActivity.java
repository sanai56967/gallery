/*
 * Copyright (C) 2003-2010 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.social.plugin;

import javax.jcr.*;
import javax.jcr.query.Query;
import javax.portlet.PortletRequest;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.ecm.jcr.model.VersionNode;
import org.exoplatform.ecm.webui.utils.Utils;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.wcm.core.NodeLocation;
import org.exoplatform.services.wcm.friendly.FriendlyService;
import org.exoplatform.services.wcm.utils.WCMCoreUtils;
import org.exoplatform.social.webui.activity.BaseUIActivity;
import org.exoplatform.social.webui.activity.UIActivitiesContainer;
import org.exoplatform.social.webui.composer.PopupContainer;
import org.exoplatform.web.application.ApplicationMessage;
import org.exoplatform.webui.application.WebuiRequestContext;
import org.exoplatform.webui.application.portlet.PortletRequestContext;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.config.annotation.EventConfig;
import org.exoplatform.webui.core.UIApplication;
import org.exoplatform.webui.core.lifecycle.UIFormLifecycle;
import org.exoplatform.webui.event.Event;
import org.exoplatform.webui.event.EventListener;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Created by The eXo Platform SAS
 * Author : Zun
 *          exo@exoplatform.com
 * Jul 23, 2010  
 */

@ComponentConfig(
   lifecycle = UIFormLifecycle.class,
   template = "classpath:groovy/social/plugin/UIDocActivity.gtmpl",
   events = {
     @EventConfig(listeners = UIDocActivity.DownloadDocumentActionListener.class),
     @EventConfig(listeners = UIDocActivity.ViewDocumentActionListener.class),
     @EventConfig(listeners = BaseUIActivity.LoadLikesActionListener.class),
     @EventConfig(listeners = BaseUIActivity.ToggleDisplayCommentFormActionListener.class),
     @EventConfig(listeners = BaseUIActivity.LikeActivityActionListener.class),
     @EventConfig(listeners = BaseUIActivity.SetCommentListStatusActionListener.class),
     @EventConfig(listeners = BaseUIActivity.PostCommentActionListener.class),
     @EventConfig(listeners = BaseUIActivity.DeleteActivityActionListener.class),
     @EventConfig(listeners = BaseUIActivity.DeleteCommentActionListener.class)
   }
 )
public class UIDocActivity extends BaseUIActivity {
  
  private static final Log LOG = ExoLogger.getLogger(UIDocActivity.class);
  private static final String IMAGE_PREFIX = "image/";
  private static final String DOCUMENT_POSTFIX = "/pdf";
  
  public static final String ACTIVITY_TYPE = "DOC_ACTIVITY1";
  public static final String GAL_ACTIVITY_TYPE = "GAL_ACTIVITY";
  public static final String DOCLINK = "DOCLINK";
  public static final String MESSAGE = "MESSAGE";
  public static final String REPOSITORY = "REPOSITORY";
  public static final String WORKSPACE = "WORKSPACE";
  public static final String DOCNAME = "DOCNAME";
  public static final String DOCPATH = "DOCPATH";
  
  public String docLink;
  public String message;
  public String docName;
  public String docPath;
  public String repository;
  public String workspace;

    public UIDocActivity() { 
    }

//    public void renderChildren(){
//        try {
//            portlet.renderChildren();
//        } catch (Exception e) {
//            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//        }
//    }

  protected boolean isPreviewable() {
    return getMimeType().endsWith(DOCUMENT_POSTFIX);    
  }
  
  protected boolean isImageFile() {
    return getMimeType().startsWith(IMAGE_PREFIX);
  }
  
  protected String getDocThumbnail(){    
    String portalContainerName = PortalContainer.getCurrentPortalContainerName();
    String restContextName = PortalContainer.getRestContextName(PortalContainer.getCurrentPortalContainerName());
      return new StringBuffer().append("/").append(PortalContainer.getCurrentPortalContainerName()).
                                 append("/").append(PortalContainer.getRestContextName(PortalContainer.getCurrentPortalContainerName())).
                                 append("/thumbnailImage/big").
                                 append("/").append(UIDocActivityComposer.REPOSITORY).
                                 append("/").append(UIDocActivityComposer.WORKSPACE).
                                 append(docPath).toString();
  }
  
  protected String getSize() {
    double size = 0;
    Node docNode = getDocNode();
    try {
      if (docNode.hasNode(Utils.JCR_CONTENT)) {
        Node contentNode = docNode.getNode(Utils.JCR_CONTENT);
        if (contentNode.hasProperty(Utils.JCR_DATA)) {
          size = contentNode.getProperty(Utils.JCR_DATA).getLength();
        }
        
        return FileUtils.byteCountToDisplaySize((long)size);
      }
    } catch (PathNotFoundException e) {
      return StringUtils.EMPTY;
    } catch (ValueFormatException e) {
      return StringUtils.EMPTY;
    } catch (RepositoryException e) {
      return StringUtils.EMPTY;
    }
    return StringUtils.EMPTY;
  }
  
  protected int getVersion() {
    try {
      VersionNode rootVersion_ = new VersionNode(NodeLocation.getNodeByLocation(new NodeLocation(repository, workspace, docPath))
                                     .getVersionHistory()
                                     .getRootVersion(), getDocNode().getSession());
      if (rootVersion_ != null) {
        return rootVersion_.getChildren().size();
      }
    } catch (UnsupportedRepositoryOperationException e) {
      return 0;
    } catch (RepositoryException e) {
      return 0;
    }
    
    return 0;
  }
  
  private boolean hasPermissionViewFile() {
    return (getDocNode() != null);
  }
  
  public static class ViewDocumentActionListener extends EventListener<UIDocActivity> {
    @Override
    public void execute(Event<UIDocActivity> event) throws Exception {
      final UIDocActivity docActivity = event.getSource();
      if (! docActivity.hasPermissionViewFile()) {
        WebuiRequestContext ctx = event.getRequestContext();
        UIApplication uiApplication = ctx.getUIApplication();
        uiApplication.addMessage(new ApplicationMessage("UIDocActivity.msg.noPermission", null, ApplicationMessage.WARNING));
        return;
      }
      final UIActivitiesContainer activitiesContainer = docActivity.getParent();
      final PopupContainer popupContainer = activitiesContainer.getPopupContainer();

      if (docActivity.getChild(UIDocViewer.class) != null) {
        docActivity.removeChild(UIDocViewer.class);
      }
      
      UIDocViewer docViewer = popupContainer.createUIComponent(UIDocViewer.class, null, "DocViewer");
      docViewer.docPath = docActivity.docPath;
      docViewer.repository = docActivity.repository;
      docViewer.workspace = docActivity.workspace;

      popupContainer.activate(docViewer, 800, 600, true);
      event.getRequestContext().addUIComponentToUpdateByAjax(popupContainer);
    }
  }
  
  public static class DownloadDocumentActionListener extends EventListener<UIDocActivity> {
    @Override
    public void execute(Event<UIDocActivity> event) throws Exception {
      UIDocActivity uiComp = event.getSource() ;
      if (! uiComp.hasPermissionViewFile()) {
        WebuiRequestContext ctx = event.getRequestContext();
        UIApplication uiApplication = ctx.getUIApplication();
        uiApplication.addMessage(new ApplicationMessage("UIDocActivity.msg.noPermission", null, ApplicationMessage.WARNING));
        return;
      }
      String downloadLink = null;
      if (getRealNode(uiComp.getDocNode()).getPrimaryNodeType().getName().equals(Utils.NT_FILE)) {
        downloadLink = Utils.getDownloadRestServiceLink(uiComp.getDocNode());
      }
      event.getRequestContext().getJavascriptManager().addJavascript("ajaxRedirect('" + downloadLink + "');");
    }
    
    private Node getRealNode(Node node) throws Exception {
      // TODO: Need to add to check symlink node
      if (node.isNodeType("nt:frozenNode")) {
        String uuid = node.getProperty("jcr:frozenUuid").getString();
        return node.getSession().getNodeByUUID(uuid);
      }
      return node;
    }
  }
  
  protected Node getDocNode() {
    NodeLocation nodeLocation = new NodeLocation(repository, workspace, docPath);
    return NodeLocation.getNodeByLocation(nodeLocation);
  }
  
  public Node getNode() {
    NodeLocation nodeLocation = new NodeLocation(repository, workspace, docPath.substring(docPath.indexOf("/", 1))); 
    return NodeLocation.getNodeByLocation(nodeLocation); 
  }

  public Node getNode1() {
    NodeLocation nodeLocation = new NodeLocation(repository, workspace, docPath);
    return NodeLocation.getNodeByLocation(new NodeLocation("repository","collaboration","/sites/intranet/web contents/site artifacts/announcements/r"));
  }

  /**
   * Gets the webdav url.
   * 
   * @param node the node
   * @return the webdav url
   * @throws Exception the exception
   */
  public String getWebdavURL() throws Exception {
    Node contentNode = getDocNode();
    NodeLocation nodeLocation = new NodeLocation(repository, workspace, docPath);
    PortletRequestContext portletRequestContext = WebuiRequestContext.getCurrentInstance();
    PortletRequest portletRequest = portletRequestContext.getRequest();
    String repository = nodeLocation.getRepository();
    String workspace = nodeLocation.getWorkspace();
    String baseURI = portletRequest.getScheme() + "://" + portletRequest.getServerName() + ":"
        + String.format("%s", portletRequest.getServerPort());

    FriendlyService friendlyService = WCMCoreUtils.getService(FriendlyService.class);
    String link = "#";

    String portalName = PortalContainer.getCurrentPortalContainerName();
    String restContextName = PortalContainer.getCurrentRestContextName();
    if (contentNode.isNodeType("nt:frozenNode")) {
      String uuid = contentNode.getProperty("jcr:frozenUuid").getString();
      Node originalNode = contentNode.getSession().getNodeByUUID(uuid);
      link = baseURI + "/" + portalName + "/" + restContextName + "/jcr/" + repository + "/"
          + workspace + originalNode.getPath() + "?version=" + contentNode.getParent().getName();
    } else {
      link = baseURI + "/" + portalName + "/" + restContextName + "/jcr/" + repository + "/"
          + workspace + contentNode.getPath();
    }

    return friendlyService.getFriendlyUri(link);
  }
//
//  public java.util.List getAttachments(){
//      return java.util.Collections.emptyList();
//  }

    public java.util.List<String> getAttachments1() {
        java.util.List<String> list = new ArrayList<String>();
        list.add(
        new StringBuffer().append("/").append(PortalContainer.getCurrentPortalContainerName()).
                                   append("/").append(PortalContainer.getRestContextName(PortalContainer.getCurrentPortalContainerName())).
                                   append("/thumbnailImage/big").
                                   append("/").append(UIDocActivityComposer.REPOSITORY).
                                   append("/").append(UIDocActivityComposer.WORKSPACE).
                                   append(docPath).toString());
        return list;

    }
    public java.util.List<Node> getAttachments3() {
        try{
     java.util.List<Node> ret = new ArrayList<Node>();  
    SessionProvider sessionProvider = WCMCoreUtils.getUserSessionProvider();
    Session session = sessionProvider.getSession("collaboration", WCMCoreUtils.getRepository());
    NodeIterator iter = session.getWorkspace().getQueryManager().createQuery("Select * from nt:file where jcr:path like' " + docPath + "/%'", Query.SQL).execute().getNodes();
    while (iter.hasNext()) {
    ret.add(iter.nextNode());
    }
    return ret;
        } catch (Exception e){
            return null;
        }
    }

    public java.util.List<Node> getAttachments() {
        try{
        Node currentNode = getNode();
        //def currentNode = uicomponent.getCurrentNode() ;
        java.util.List attachments = new ArrayList<Node>() ;
        javax.jcr.NodeIterator childrenIterator = currentNode.getNodes();
        int attachData =0 ;

        while (childrenIterator.hasNext()) {
          javax.jcr.Node childNode = childrenIterator.nextNode();
          String nodeType = childNode.getPrimaryNodeType().getName();
            if (nodeType.equalsIgnoreCase("nt:file")) {
//            if (((Node)attachments.get(4)).getName().indexOf(".") != -1) {
//            if (childNode.hasProperty(Utils.JCR_DATA)) {
//              attachData = childNode.getProperty(Utils.JCR_DATA).getStream().available();
//              if (attachData > 0)
                attachments.add(childNode);
//            } else {
//              attachments.add(childNode);
            }
        }
        return attachments;
        } catch (Exception e){

        }
        return Collections.emptyList();
    }


  /**
   * Gets the summary.
   * 
   * @param node the node
   * @return the summary of Node. Return empty string if catch an exception.
   */
  public String getSummary() {
    String desc = "";
    Node node = getDocNode();
    try {
      if (node != null) {
        if (node.hasProperty("exo:summary")) {
          desc = node.getProperty("exo:summary").getValue().getString();
        } else if (node.hasNode("jcr:content")) {
          Node content = node.getNode("jcr:content");
          if (content.hasProperty("dc:description") && content.getProperty("dc:description").getValues().length > 0) {
            desc = content.getProperty("dc:description").getValues()[0].getString();
          }
        }
      }
    } catch (RepositoryException re) {
      if (LOG.isWarnEnabled())
        LOG.warn("RepositoryException: ", re);
    }

    return desc;
  }
  
  public String getTitle() throws Exception {
    return docPath.substring(docPath.lastIndexOf("/")+1);
//    return Utils.getTitle(getDocNode());
  }
  
  private String getMimeType() {

    String mimeType = "gallery";    
//      try {
//        mimeType = getDocNode().getNode("jcr:content").getProperty("jcr:mimeType").getString();
//      } catch (ValueFormatException e) {
//        if (LOG.isDebugEnabled())
//          LOG.debug(e);
//        return StringUtils.EMPTY;
//      } catch (PathNotFoundException e) {
//        if (LOG.isDebugEnabled())
//          LOG.debug(e);
//        return StringUtils.EMPTY;
//      } catch (RepositoryException e) {
//        if (LOG.isDebugEnabled())
//          LOG.debug(e);
//        return StringUtils.EMPTY;
//      }
    return mimeType;
  }
}
