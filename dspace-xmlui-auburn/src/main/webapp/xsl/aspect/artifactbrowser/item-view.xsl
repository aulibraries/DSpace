<!--

    The contents of this file are subject to the license and copyright
    detailed in the LICENSE and NOTICE files at the root of the source
    tree and available online at

    http://www.dspace.org/license/

-->

<!--
    Rendering specific to the item display page.

    Author: art.lowel at atmire.com
    Author: lieven.droogmans at atmire.com
    Author: ben at atmire.com
    Author: Alexey Maslov

-->

<xsl:stylesheet xmlns:i18n="http://apache.org/cocoon/i18n/2.1" 
    xmlns:dri="http://di.tamu.edu/DRI/1.0/" 
    xmlns:mets="http://www.loc.gov/METS/" 
    xmlns:dim="http://www.dspace.org/xmlns/dspace/dim" 
    xmlns:xlink="http://www.w3.org/TR/xlink/" 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0" 
    xmlns:atom="http://www.w3.org/2005/Atom" 
    xmlns:ore="http://www.openarchives.org/ore/terms/" 
    xmlns:oreatom="http://www.openarchives.org/ore/atom/" 
    xmlns="http://www.w3.org/1999/xhtml" 
    xmlns:xalan="http://xml.apache.org/xalan" 
    xmlns:encoder="xalan://java.net.URLEncoder" 
    xmlns:util="org.dspace.app.xmlui.utils.XSLUtils" 
    xmlns:jstring="java.lang.String" 
    xmlns:rights="http://cosimo.stanford.edu/sdr/metsrights/" 
    xmlns:confman="org.dspace.core.ConfigurationManager" exclude-result-prefixes="xalan encoder i18n dri mets dim xlink xsl util jstring rights confman">

    <xsl:output indent="yes" />

    <xsl:variable name="serverName" select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@qualifier='serverName']" />
    <xsl:variable name="contextPath" select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element = 'contextPath']" />
    <xsl:variable name="requestURI" select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='request'][@qualifier='URI']"></xsl:variable>
    <xsl:variable name="referenceSetID" select="/dri:document/dri:body/dri:div/dri:referenceSet/@id" />
    <xsl:variable name="repoName">
        <xsl:choose>
            <xsl:when test="$serverName = 'etd.auburn.edu' or $contextPath = '/auetd'">
                <xsl:text>AUETD</xsl:text>
            </xsl:when>
            <xsl:when test="$serverName = 'aurora.auburn.edu' or $serverName = 'mrads.lib.auburn.edu' or contains($contextPath, 'aurora')">
                <xsl:text>AUrora</xsl:text>
            </xsl:when>
            <xsl:when test="$serverName = 'deepspace.lib.auburn.edu' or $contextPath = '/deepspace'">
                <xsl:text>DeepSpace</xsl:text>
            </xsl:when>
        </xsl:choose>
    </xsl:variable>

    <xsl:template name="itemSummaryView-DIM">
        <xsl:param name="itemParentCollectionHandleID" />
        <!-- Generate the info about the item from the metadata section -->
        <xsl:apply-templates select="./mets:dmdSec/mets:mdWrap[@OTHERMDTYPE='DIM']/mets:xmlData/dim:dim" mode="itemSummaryView-DIM">
            <xsl:with-param name="itemParentCollectionHandleId" select="$itemParentCollectionHandleId" />
        </xsl:apply-templates>

        <xsl:copy-of select="$SFXLink" />

        <!-- Generate the Creative Commons license information from the file section (DSpace deposit license hidden by default)-->
        <xsl:if test="./mets:fileSec/mets:fileGrp[@USE='CC-LICENSE' or @USE='LICENSE']">
            <div class="license-info">
                <p>
                    <i18n:text>xmlui.dri2xhtml.METS-1.0.license-text</i18n:text>
                </p>
                <ul class="list-unstyled">
                    <xsl:apply-templates select="./mets:fileSec/mets:fileGrp[@USE='CC-LICENSE' or @USE='LICENSE']" mode="simple" />
                </ul>
            </div>
        </xsl:if>
    </xsl:template>

    <!-- An item rendered in the detailView pattern, the "full item record" view of a DSpace item in Manakin. -->
    <xsl:template name="itemDetailView-DIM">
        <!-- Output all of the metadata about the item from the metadata section -->
        <xsl:apply-templates select="mets:dmdSec/mets:mdWrap[@OTHERMDTYPE='DIM']/mets:xmlData/dim:dim" mode="itemDetailView-DIM" />

        <!-- Generate the bitstream information from the file section -->
        <xsl:choose>
            <xsl:when test="./mets:fileSec/mets:fileGrp[@USE='CONTENT' or @USE='ORIGINAL' or @USE='LICENSE']/mets:file">
                <h2 class="h3">
                    <i18n:text>xmlui.dri2xhtml.METS-1.0.item-files-head</i18n:text>
                </h2>
                <div class="file-list">
                    <xsl:apply-templates select="./mets:fileSec/mets:fileGrp[@USE='CONTENT' or @USE='ORIGINAL' or @USE='LICENSE' or @USE='CC-LICENSE' or @USE='TEXT']">
                        <xsl:with-param name="context" select="." />
                        <xsl:with-param name="primaryBitstream" select="./mets:structMap[@TYPE='LOGICAL']/mets:div[@TYPE='DSpace Item']/mets:fptr/@FILEID" />
                    </xsl:apply-templates>
                </div>
            </xsl:when>
            <!-- Special case for handling ORE resource maps stored as DSpace bitstreams -->
            <xsl:when test="./mets:fileSec/mets:fileGrp[@USE='ORE']">
                <xsl:apply-templates select="./mets:fileSec/mets:fileGrp[@USE='ORE']" mode="itemDetailView-DIM" />
            </xsl:when>
            <xsl:otherwise>
                <h2>
                    <i18n:text>xmlui.dri2xhtml.METS-1.0.item-files-head</i18n:text>
                </h2>
                <table class="ds-table file-list">
                    <tr class="ds-table-header-row">
                        <th>
                            <i18n:text>xmlui.dri2xhtml.METS-1.0.item-files-file</i18n:text>
                        </th>
                        <th>
                            <i18n:text>xmlui.dri2xhtml.METS-1.0.item-files-size</i18n:text>
                        </th>
                        <th>
                            <i18n:text>xmlui.dri2xhtml.METS-1.0.item-files-format</i18n:text>
                        </th>
                        <th>
                            <i18n:text>xmlui.dri2xhtml.METS-1.0.item-files-view</i18n:text>
                        </th>
                    </tr>
                    <tr>
                        <td colspan="4">
                            <p>
                                <i18n:text>xmlui.dri2xhtml.METS-1.0.item-no-files</i18n:text>
                            </p>
                        </td>
                    </tr>
                </table>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="dim:dim" mode="itemSummaryView-DIM">
        <xsl:param name="itemParentCollectionHandleId" />
        <xsl:variable name="endDateTs">
            <xsl:copy-of select="$document//dri:meta/dri:pageMeta/dri:metadata[@element='enddateTs']/node()" />
        </xsl:variable>
        <xsl:variable name="currentTs">
            <xsl:copy-of select="$document//dri:meta/dri:pageMeta/dri:metadata[@element='currentTs']/node()" />
        </xsl:variable>
        <!-- <div class="item-summary-view-metadata"> -->
        <xsl:call-template name="itemSummaryView-DIM-title" />
        <div class="row">
            <div class="col-sm-4">
                <xsl:call-template name="itemSummaryView-DIM-file-section" />
                <xsl:call-template name="itemSummaryView-DIM-date" />
                <xsl:call-template name="itemSummaryView-DIM-authors" />
                <xsl:choose>
                    <xsl:when test="$repoName = 'AUETD'">
                        <xsl:call-template name="itemSummaryView-DIM-type" />
                        <xsl:call-template name="itemSummaryView-DIM-subject" />
                        <xsl:choose>
                            <xsl:when test="not(contains($requestURI, 'workflow')) and not(contains($referenceSetID, 'administrative.item.ViewItem'))">
                                <xsl:choose>
                                    <xsl:when test="dim:field[@qualifier='status']/node() = 'EMBARGOED'">
                                        <xsl:choose>
                                            <xsl:when test="$endDateTs != ''">
                                                <xsl:if test="$endDateTs &gt; $currentTs">
                                                    <xsl:call-template name="itemSummaryView-DIM-embargo-status" />
                                                    <xsl:call-template name="itemSummaryView-DIM-embargo-rights" />
                                                    <xsl:call-template name="itemSummaryView-DIM-embargo-enddate" />
                                                </xsl:if>
                                            </xsl:when>
                                            <xsl:otherwise />
                                        </xsl:choose>
                                    </xsl:when>
                                    <xsl:otherwise />
                                </xsl:choose>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:call-template name="itemSummaryView-DIM-embargo-status" />
                                <xsl:call-template name="itemSummaryView-DIM-embargo-rights" />
                                <xsl:call-template name="itemSummaryView-DIM-embargo-enddate" />
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:when>
                    <xsl:when test="$repoName = 'AUrora'">
                        <xsl:variable name="collectionID">
                            <xsl:copy-of select="substring-after($document//dri:meta/dri:pageMeta/dri:metadata[@element='focus'][@qualifier='container']/node(), ':')" />
                        </xsl:variable>
                        <xsl:if test="$collectionID = '11200/8' or $collectionID = '11200/44653'">
                            <xsl:call-template name="itemSummaryView-DIM-language" />
                        </xsl:if>
                    </xsl:when>
                </xsl:choose>
                <xsl:if test="$ds_item_view_toggle_url != ''">
                    <xsl:call-template name="itemSummaryView-show-full" />
                </xsl:if>
            </div>
            <div class="col-sm-8">
                <xsl:choose>
                    <xsl:when test="$repoName != 'DeepSpace'">
                        <xsl:call-template name="itemSummaryView-DIM-abstract" />
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:call-template name="itemSummaryView-DIM-description"/>
                        <xsl:if test="not($itemParentCollectionHandleId = '123456789/4')">
                            <h2 class="h3"><i18n:text>xmlui.dri2xhtml.METS-1.0.item-files-head</i18n:text></h2>
                            <div class="file-list">
                                <xsl:apply-templates select="//mets:fileSec/mets:fileGrp[@USE='CONTENT' or @USE='ORIGINAL' or @USE='LICENSE' or @USE='CC-LICENSE']">
                                    <xsl:with-param name="context" select="/mets:METS"/>
                                    <xsl:with-param name="primaryBitstream" select="//mets:structMap[@TYPE='LOGICAL']/mets:div[@TYPE='DSpace Item']/mets:fptr/@FILEID"/>
                                </xsl:apply-templates>
                            </div>
                        </xsl:if>
                    </xsl:otherwise>
                </xsl:choose>
                <xsl:if test="not($repoName = 'DeepSpace')">
                    <xsl:call-template name="itemSummaryView-DIM-URI" />
                </xsl:if>
                <xsl:choose>
                    <xsl:when test="$repoName = 'AUETD'">
                        <xsl:if test="$document//dri:meta/dri:userMeta/dri:metadata[@qualifier='admin'] = 'yes'">
                            <xsl:call-template name="itemSummaryView-collections" />
                        </xsl:if>
                    </xsl:when>
                    <xsl:when test="$repoName = 'AUrora' or $repoName = 'DeepSpace'">
                        <xsl:call-template name="itemSummaryView-collections" />
                    </xsl:when>
                </xsl:choose>
            </div>
        </div>
    </xsl:template>

    <xsl:template name="itemSummaryView-DIM-title">
        <xsl:choose>
            <xsl:when test="count(dim:field[@element='title'][not(@qualifier)]) &gt; 1">
                <h1 class="h2">
                    <xsl:value-of select="dim:field[@element='title'][not(@qualifier)][1]/node()" />
                </h1>
                <p class="lead">
                    <xsl:for-each select="dim:field[@element='title'][not(@qualifier)]">
                        <xsl:if test="not(position() = 1)">
                            <xsl:value-of select="./node()" />
                            <xsl:if test="count(following-sibling::dim:field[@element='title'][not(@qualifier)]) != 0">
                                <xsl:text>; </xsl:text>
                                <br />
                            </xsl:if>
                        </xsl:if>
                    </xsl:for-each>
                </p>
            </xsl:when>
            <xsl:when test="count(dim:field[@element='title'][not(@qualifier)]) = 1">
                <h1 class="first-page-header">
                    <xsl:value-of select="dim:field[@element='title'][not(@qualifier)][1]/node()" />
                </h1>
            </xsl:when>
            <xsl:otherwise>
                <h1 class="first-page-header">
                    <i18n:text>xmlui.dri2xhtml.METS-1.0.no-title</i18n:text>
                </h1>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="itemSummaryView-DIM-thumbnail">
        <div class="thumbnail">
            <xsl:choose>
                <xsl:when test="//mets:fileSec/mets:fileGrp[@USE='THUMBNAIL']">
                    <xsl:variable name="src">
                        <xsl:choose>
                            <xsl:when test="/mets:METS/mets:fileSec/mets:fileGrp[@USE='THUMBNAIL']/mets:file[@GROUPID=../../mets:fileGrp[@USE='CONTENT']/mets:file[@GROUPID=../../mets:fileGrp[@USE='THUMBNAIL']/mets:file/@GROUPID][1]/@GROUPID]">
                                <xsl:value-of select="/mets:METS/mets:fileSec/mets:fileGrp[@USE='THUMBNAIL']/mets:file[@GROUPID=../../mets:fileGrp[@USE='CONTENT']/mets:file[@GROUPID=../../mets:fileGrp[@USE='THUMBNAIL']/mets:file/@GROUPID][1]/@GROUPID]/mets:FLocat[@LOCTYPE='URL']/@xlink:href" />
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:value-of select="//mets:fileSec/mets:fileGrp[@USE='THUMBNAIL']/mets:file/mets:FLocat[@LOCTYPE='URL']/@xlink:href" />
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>
                    <img class="img-thumbnail" alt="Thumbnail">
                        <xsl:attribute name="src">
                            <xsl:value-of select="$src" />
                        </xsl:attribute>
                    </img>
                </xsl:when>
                <xsl:otherwise>
                    <img class="img-thumbnail" alt="Thumbnail">
                        <xsl:attribute name="data-src">
                            <xsl:text>holder.js/100%x</xsl:text>
                            <xsl:value-of select="$thumbnail.maxheight" />
                            <xsl:text>/text:No Thumbnail</xsl:text>
                        </xsl:attribute>
                    </img>
                </xsl:otherwise>
            </xsl:choose>
        </div>
    </xsl:template>

    <xsl:template name="itemSummaryView-DIM-abstract">
        <xsl:if test="dim:field[@element='description' and @qualifier='abstract']">
            <div class="simple-item-view-description item-page-field-wrapper">
                <h2 class="visible-xs h5">
                    <i18n:text>xmlui.dri2xhtml.METS-1.0.item-abstract</i18n:text>
                </h2>
                <xsl:for-each select="dim:field[@element='description' and @qualifier='abstract']">
                    <xsl:choose>
                        <xsl:when test="node()">
                            <p>
                                <xsl:copy-of select="node()" />
                            </p>
                        </xsl:when>
                        <xsl:otherwise />
                    </xsl:choose>
                    <xsl:if test="count(following-sibling::dim:field[@element='description' and @qualifier='abstract']) != 0">
                        <div class="spacer">&#160;</div>
                    </xsl:if>
                </xsl:for-each>
                <xsl:if test="count(dim:field[@element='description' and @qualifier='abstract']) &gt; 1">
                    <div class="spacer">&#160;</div>
                </xsl:if>
            </div>
        </xsl:if>
    </xsl:template>

    <xsl:template name="itemSummaryView-DIM-authors">
        <xsl:if test="dim:field[@element='contributor'][@qualifier='author' and descendant::text()] or dim:field[@element='creator' and descendant::text()] or dim:field[@element='contributor' and descendant::text()]">
            <div class="simple-item-view-authors item-page-field-wrapper">
                <h2 class="h5">
                    <i18n:text>xmlui.dri2xhtml.METS-1.0.item-author</i18n:text>
                </h2>
                <xsl:choose>
                    <xsl:when test="dim:field[@element='contributor'][@qualifier='author']">
                        <xsl:for-each select="dim:field[@element='contributor'][@qualifier='author']">
                            <xsl:call-template name="itemSummaryView-DIM-authors-entry" />
                        </xsl:for-each>
                    </xsl:when>
                    <xsl:when test="dim:field[@element='creator']">
                        <xsl:for-each select="dim:field[@element='creator']">
                            <xsl:call-template name="itemSummaryView-DIM-authors-entry" />
                        </xsl:for-each>
                    </xsl:when>
                    <xsl:when test="dim:field[@element='contributor']">
                        <xsl:for-each select="dim:field[@element='contributor']">
                            <xsl:call-template name="itemSummaryView-DIM-authors-entry" />
                        </xsl:for-each>
                    </xsl:when>
                    <xsl:otherwise>
                        <i18n:text>xmlui.dri2xhtml.METS-1.0.no-author</i18n:text>
                    </xsl:otherwise>
                </xsl:choose>
            </div>
        </xsl:if>
    </xsl:template>

    <xsl:template name="itemSummaryView-DIM-authors-entry">
        <div>
            <xsl:if test="@authority">
                <xsl:attribute name="class">
                    <xsl:text>ds-dc_contributor_author-authority</xsl:text>
                </xsl:attribute>
            </xsl:if>
            <xsl:copy-of select="node()" />
        </div>
    </xsl:template>

    <xsl:template name="itemSummaryView-DIM-URI">
        <xsl:if test="dim:field[@element='identifier' and @qualifier='uri' and descendant::text()]">
            <div class="item-page-field-wrapper">
                <h2 class="h5">
                    <i18n:text>xmlui.dri2xhtml.METS-1.0.item-uri</i18n:text>
                </h2>
                <xsl:choose>
                    <xsl:when test="count(dim:field[@element='identifier' and @qualifier='uri']) != 0">
                        <xsl:choose>
                            <xsl:when test="count(dim:field[@element='identifier' and @qualifier='uri']) &gt; 1">
                                <ul>
                                    <xsl:for-each select="dim:field[@element='identifier' and @qualifier='uri']">
                                        <li>
                                            <a>
                                                <xsl:attribute name="href">
                                                    <xsl:copy-of select="node()" />
                                                </xsl:attribute>
                                                <xsl:copy-of select="node()" />
                                            </a>
                                        </li>
                                    </xsl:for-each>
                                </ul>
                            </xsl:when>
                            <xsl:otherwise>
                                <p>
                                    <a>
                                        <xsl:attribute name="href">
                                            <xsl:copy-of select="dim:field[@element='identifier' and @qualifier='uri']/node()" />
                                        </xsl:attribute>
                                        <xsl:copy-of select="dim:field[@element='identifier' and @qualifier='uri']/node()" />
                                    </a>
                                </p>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:when>
                    <xsl:otherwise />
                </xsl:choose>
            </div>
        </xsl:if>
    </xsl:template>

    <xsl:template name="itemSummaryView-DIM-date">
        <xsl:if test="dim:field[@element='date' and @qualifier='issued' and descendant::text()]">
            <div class="word-break item-page-field-wrapper">
                <h2 class="h5">
                    <i18n:text>xmlui.dri2xhtml.METS-1.0.item-date</i18n:text>
                </h2>
                <xsl:for-each select="dim:field[@element='date' and @qualifier='issued']">
                    <xsl:copy-of select="substring(./node(),1,10)" />
                    <xsl:if test="count(following-sibling::dim:field[@element='date' and @qualifier='issued']) != 0">
                        <br />
                    </xsl:if>
                </xsl:for-each>
            </div>
        </xsl:if>
    </xsl:template>

    <xsl:template name="itemSummaryView-show-full">
        <div class="item-page-field-wrapper">
            <h2 class="h5">
                <i18n:text>xmlui.mirage2.itemSummaryView.MetaData</i18n:text>
            </h2>
            <a>
                <xsl:attribute name="href">
                    <xsl:value-of select="$ds_item_view_toggle_url" />
                </xsl:attribute>
                <i18n:text>xmlui.ArtifactBrowser.ItemViewer.show_full</i18n:text>
            </a>
        </div>
    </xsl:template>

    <xsl:template name="itemSummaryView-collections">
        <xsl:if test="$document//dri:referenceSet[@id='aspect.artifactbrowser.ItemViewer.referenceSet.collection-viewer']">
            <div class="item-page-field-wrapper">
                <h2 class="h5">
                    <i18n:text>xmlui.mirage2.itemSummaryView.Collections</i18n:text>
                </h2>
                <xsl:apply-templates select="$document//dri:referenceSet[@id='aspect.artifactbrowser.ItemViewer.referenceSet.collection-viewer']/dri:reference" />
            </div>
        </xsl:if>
    </xsl:template>

    <xsl:template name="itemSummaryView-DIM-language">
        <div class="word-break item-page-field-wrapper">
            <h2 class="h5">Language</h2>
            <xsl:if test="dim:field[@element='language' and descendant::text()]">
                <xsl:value-of select="dim:field[@element='language'][not(@qualifier)]/node()" />
            </xsl:if>
        </div>
    </xsl:template>

    <xsl:template name="itemSummaryView-DIM-file-section">
        <xsl:choose>
            <xsl:when test="//mets:fileSec/mets:fileGrp[@USE='CONTENT' or @USE='ORIGINAL' or @USE='LICENSE' or @USE='TEXT']/mets:file">
                <div class="item-page-field-wrapper clearfix">
                    <h2 class="h5">
                        <i18n:text>xmlui.dri2xhtml.METS-1.0.item-files-viewOpen</i18n:text>
                    </h2>

                    <xsl:variable name="label-1">
                        <xsl:choose>
                            <xsl:when test="confman:getProperty('mirage2.item-view.bitstream.href.label.1')">
                                <xsl:value-of select="confman:getProperty('mirage2.item-view.bitstream.href.label.1')" />
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:text>label</xsl:text>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>

                    <xsl:variable name="label-2">
                        <xsl:choose>
                            <xsl:when test="confman:getProperty('mirage2.item-view.bitstream.href.label.2')">
                                <xsl:value-of select="confman:getProperty('mirage2.item-view.bitstream.href.label.2')" />
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:text>title</xsl:text>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>

                    <xsl:for-each select="//mets:fileSec/mets:fileGrp[@USE='CONTENT' or @USE='ORIGINAL' or @USE='LICENSE' or @USE='TEXT']/mets:file">
                        <xsl:call-template name="itemSummaryView-DIM-file-section-entry">
                            <xsl:with-param name="href" select="mets:FLocat[@LOCTYPE='URL']/@xlink:href" />
                            <xsl:with-param name="mimetype" select="@MIMETYPE" />
                            <xsl:with-param name="label-1" select="$label-1" />
                            <xsl:with-param name="label-2" select="$label-2" />
                            <xsl:with-param name="title" select="mets:FLocat[@LOCTYPE='URL']/@xlink:title" />
                            <xsl:with-param name="label" select="mets:FLocat[@LOCTYPE='URL']/@xlink:label" />
                            <xsl:with-param name="size" select="@SIZE" />
                        </xsl:call-template>
                    </xsl:for-each>
                </div>
            </xsl:when>
            <!-- Special case for handling ORE resource maps stored as DSpace bitstreams -->
            <xsl:when test="//mets:fileSec/mets:fileGrp[@USE='ORE']">
                <xsl:apply-templates select="//mets:fileSec/mets:fileGrp[@USE='ORE']" mode="itemSummaryView-DIM" />
            </xsl:when>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="itemSummaryView-DIM-file-section-entry">
        <xsl:param name="href" />
        <xsl:param name="mimetype" />
        <xsl:param name="label-1" />
        <xsl:param name="label-2" />
        <xsl:param name="title" />
        <xsl:param name="label" />
        <xsl:param name="size" />
        <div>
            <a>
                <xsl:attribute name="href">
                    <xsl:value-of select="$href" />
                </xsl:attribute>
                <xsl:if test="$repoName = 'AUETD'">
                    <xsl:attribute name="class">
                        <xsl:text>pdf_file</xsl:text>
                    </xsl:attribute>
                </xsl:if>
                <xsl:attribute name="aria-label">
                    <xsl:choose>
                        <xsl:when test="contains(mets:FLocat[@LOCTYPE='URL']/@xlink:href,'isAllowed=n')">
                            <xsl:text>Access to this file is restricted.</xsl:text>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:text>Download </xsl:text>
                            <xsl:value-of select="mets:FLocat[@LOCTYPE='URL']/@xlink:title" />
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:attribute>
                <xsl:call-template name="getFileIcon">
                    <xsl:with-param name="mimetype">
                        <xsl:value-of select="substring-before($mimetype,'/')" />
                        <xsl:text>/</xsl:text>
                        <xsl:value-of select="substring-after($mimetype,'/')" />
                    </xsl:with-param>
                </xsl:call-template>
                <xsl:choose>
                    <xsl:when test="contains($label-1, 'title') and string-length($title)!=0">
                        <xsl:value-of select="$title" />
                    </xsl:when>
                    <xsl:when test="contains($label-2, 'title') and string-length($title)!=0">
                        <xsl:value-of select="$title" />
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:call-template name="getFileTypeDesc">
                            <xsl:with-param name="mimetype">
                                <xsl:value-of select="substring-before($mimetype,'/')" />
                                <xsl:text>/</xsl:text>
                                <xsl:choose>
                                    <xsl:when test="contains($mimetype,';')">
                                        <xsl:value-of select="substring-before(substring-after($mimetype,'/'),';')" />
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <xsl:value-of select="substring-after($mimetype,'/')" />
                                    </xsl:otherwise>
                                </xsl:choose>
                            </xsl:with-param>
                        </xsl:call-template>
                    </xsl:otherwise>
                </xsl:choose>
                <xsl:text> (</xsl:text>
                <xsl:choose>
                    <xsl:when test="$size &lt; 1024">
                        <xsl:value-of select="$size" />
                        <i18n:text>xmlui.dri2xhtml.METS-1.0.size-bytes</i18n:text>
                    </xsl:when>
                    <xsl:when test="$size &lt; 1024 * 1024">
                        <xsl:value-of select="substring(string($size div 1024),1,5)" />
                        <i18n:text>xmlui.dri2xhtml.METS-1.0.size-kilobytes</i18n:text>
                    </xsl:when>
                    <xsl:when test="$size &lt; 1024 * 1024 * 1024">
                        <xsl:value-of select="substring(string($size div (1024 * 1024)),1,5)" />
                        <i18n:text>xmlui.dri2xhtml.METS-1.0.size-megabytes</i18n:text>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="substring(string($size div (1024 * 1024 * 1024)),1,5)" />
                        <i18n:text>xmlui.dri2xhtml.METS-1.0.size-gigabytes</i18n:text>
                    </xsl:otherwise>
                </xsl:choose>
                <xsl:text>)</xsl:text>
            </a>
        </div>
    </xsl:template>

    <xsl:template name="itemSummaryView-DIM-description">
        <xsl:if test="dim:field[@element='description'][not(@qualifier)]">
            <div class="simple-item-view-description item-page-field-wrapper table">
                <h5 class="visible-xs"><i18n:text>xmlui.dri2xhtml.METS-1.0.item-description</i18n:text></h5>
                <xsl:choose>
                    <xsl:when test="dim:field[@element='description'][not(@qualifier)]/node() != ''">
                        <xsl:copy-of select="dim:field[@element='description'][not(@qualifier)]/node()" />
                    </xsl:when>
                    <xsl:otherwise>
                        &#160;
                    </xsl:otherwise>
                </xsl:choose>
            </div>
        </xsl:if>
    </xsl:template>

    <xsl:template name="itemSummaryView-DIM-embargo-status">
        <xsl:if test="dim:field[@element='embargo' and @qualifier='status' and descendant::text()]">
            <div class="simple-item-view word-break item-page-field-wrapper">
                <h2 class="h5">Restriction Status</h2>
                <xsl:choose>
                    <xsl:when test="dim:field[@qualifier='status']/node() != ''">
                        <xsl:copy-of select="dim:field[@qualifier='status']/node()" />
                    </xsl:when>
                    <xsl:otherwise>
                        &#160;
                    </xsl:otherwise>
                </xsl:choose>
            </div>
        </xsl:if>
    </xsl:template>

    <xsl:template name="itemSummaryView-DIM-embargo-enddate">
        <xsl:if test="dim:field[@element='embargo' and @qualifier='enddate' and descendant::text()]">
            <div class="simple-item-view-date word-break item-page-field-wrapper">
                <h2 class="h5">Date Available</h2>
                <xsl:choose>
                    <xsl:when test="dim:field[@qualifier='enddate']/node() != ''">
                        <xsl:call-template name="formatDate">
                            <xsl:with-param name="tempDate" select="dim:field[@qualifier='enddate']/node()" />
                        </xsl:call-template>
                    </xsl:when>
                    <xsl:otherwise>
                        &#160;
                    </xsl:otherwise>
                </xsl:choose>
            </div>
        </xsl:if>
    </xsl:template>

    <xsl:template name="itemSummaryView-DIM-embargo-rights">
        <xsl:if test="dim:field[@element='rights' and descendant::text()]">
            <div class="simple-item-view word-break item-page-field-wrapper">
                <h2 class="h5">Restriction Type</h2>
                <xsl:choose>
                    <xsl:when test="dim:field[@element='rights']/node() != ''">
                        <xsl:choose>
                            <xsl:when test="dim:field[@element='rights']/node() = 'EMBARGO_NOT_AUBURN'">
                                <xsl:text>Auburn University Users</xsl:text>
                            </xsl:when>
                            <xsl:when test="dim:field[@element='rights']/node() = 'EMBARGO_GLOBAL'">
                                <xsl:text>Full</xsl:text>
                            </xsl:when>
                            <xsl:otherwise />
                        </xsl:choose>
                    </xsl:when>
                    <xsl:otherwise>
                        &#160;
                    </xsl:otherwise>
                </xsl:choose>
            </div>
        </xsl:if>
    </xsl:template>

    <xsl:template name="itemSummaryView-DIM-subject">
        <xsl:variable name="header">
            <xsl:choose>
                <xsl:when test="$repoName = 'AUETD'">
                    <xsl:text>Department</xsl:text>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:text>Subject</xsl:text>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:if test="dim:field[@element='subject' and descendant::text()]">
            <div class="simple-item-view-date word-break item-page-field-wrapper">
                <h2 class="h5">
                    <xsl:value-of select="$header" />
                </h2>
                <p>
                    <xsl:for-each select="dim:field[@element='subject']">
                        <xsl:copy-of select="./node()" />
                        <xsl:if test="count(following-sibling::dim:field[@element='subject']) != 0">
                            <br />
                        </xsl:if>
                    </xsl:for-each>
                </p>
            </div>
        </xsl:if>
    </xsl:template>

    <xsl:template name="itemSummaryView-DIM-type">
        <xsl:if test="dim:field[@element='type' and descendant::text()]">
            <div class="simple-item-view-date word-break item-page-field-wrapper">
                <h2 class="h5">Type of Degree</h2>
                <xsl:choose>
                    <xsl:when test="dim:field[@element='type']/node() != ''">
                        <xsl:copy-of select="dim:field[@element='type']/node()" />
                    </xsl:when>
                    <xsl:otherwise>
                        &#160;
                    </xsl:otherwise>
                </xsl:choose>
            </div>
        </xsl:if>
    </xsl:template>

    <xsl:template match="dim:dim" mode="itemDetailView-DIM">
        <xsl:call-template name="itemSummaryView-DIM-title" />
        <div class="ds-table-responsive">
            <table class="ds-includeSet-table detailtable table table-striped table-hover">
                <tr>
                    <th>Metadata Field</th>
                    <th>Value</th>
                    <th>Language</th>
                </tr>
                <xsl:apply-templates mode="itemDetailView-DIM" />
            </table>
        </div>
        <span class="Z3988">
            <xsl:attribute name="title">
                <xsl:call-template name="renderCOinS" />
            </xsl:attribute>
            &#xFEFF;                         <!-- non-breaking space to force separating the end tag -->
        </span>
        <xsl:copy-of select="$SFXLink" />
    </xsl:template>

    <xsl:template match="dim:field" mode="itemDetailView-DIM">
        <tr>
            <xsl:attribute name="class">
                <xsl:text>ds-table-row </xsl:text>
                <xsl:if test="(position() div 2 mod 2 = 0)">even </xsl:if>
                <xsl:if test="(position() div 2 mod 2 = 1)">odd </xsl:if>
            </xsl:attribute>
            <td class="label-cell">
                <xsl:value-of select="./@mdschema" />
                <xsl:text>.</xsl:text>
                <xsl:value-of select="./@element" />
                <xsl:if test="./@qualifier">
                    <xsl:text>.</xsl:text>
                    <xsl:value-of select="./@qualifier" />
                </xsl:if>
            </td>
            <td class="word-break">
                <xsl:copy-of select="./node()" />
            </td>
            <td>
                <xsl:value-of select="./@language" />
            </td>
        </tr>
    </xsl:template>

    <!-- don't render the item-view-toggle automatically in the summary view, only when it gets called -->
    <xsl:template match="dri:p[contains(@rend , 'item-view-toggle') and
        (preceding-sibling::dri:referenceSet[@type = 'summaryView'] or following-sibling::dri:referenceSet[@type = 'summaryView'])]">
    </xsl:template>

    <!-- don't render the head on the item view page -->
    <xsl:template match="dri:div[@n='item-view']/dri:head" priority="5">
    </xsl:template>

    <xsl:template match="mets:fileGrp[@USE='CONTENT']">
        <xsl:param name="context" />
        <xsl:param name="primaryBitstream" select="-1" />
        <xsl:choose>
            <!-- If one exists and it's of text/html MIME type, only display the primary bitstream -->
            <xsl:when test="mets:file[@ID=$primaryBitstream]/@MIMETYPE='text/html'">
                <xsl:apply-templates select="mets:file[@ID=$primaryBitstream]">
                    <xsl:with-param name="context" select="$context" />
                </xsl:apply-templates>
            </xsl:when>
            <!-- Otherwise, iterate over and display all of them -->
            <xsl:otherwise>
                <xsl:apply-templates select="mets:file">
                    <!--Do not sort any more bitstream order can be changed-->
                    <xsl:with-param name="context" select="$context" />
                </xsl:apply-templates>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="mets:fileGrp[@USE='LICENSE']">
        <xsl:param name="context" />
        <xsl:param name="primaryBitstream" select="-1" />
        <xsl:apply-templates select="mets:file">
            <xsl:with-param name="context" select="$context" />
        </xsl:apply-templates>
    </xsl:template>

    <xsl:template match="mets:file">
        <xsl:param name="context" select="." />
        <xsl:variable name="imageBlockClasses">
            <xsl:choose>
                <xsl:when test="$context/mets:fileSec/mets:fileGrp[@USE='THUMBNAIL']/mets:file[@GROUPID=current()/@GROUPID]">
                    <xsl:text>col-xs-6 col-sm-3</xsl:text>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:text>col-xs-2 col-sm-2</xsl:text>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:variable name="contentBlockClasses">
            <xsl:choose>
                <xsl:when test="$context/mets:fileSec/mets:fileGrp[@USE='THUMBNAIL']/mets:file[@GROUPID=current()/@GROUPID]">
                    <xsl:text>col-xs-6 col-sm-7 col-sm-pull-1</xsl:text>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:text>col-sm-10 col-sm-pull-2</xsl:text>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <div class="file-wrapper row">
            <div>
                <xsl:attribute name="class">
                    <xsl:value-of select="$imageBlockClasses" />
                </xsl:attribute>
                <xsl:attribute name="href">
                    <xsl:value-of select="mets:FLocat[@LOCTYPE='URL']/@xlink:href" />
                </xsl:attribute>
                <xsl:choose>
                    <xsl:when test="$context/mets:fileSec/mets:fileGrp[@USE='THUMBNAIL']/mets:file[@GROUPID=current()/@GROUPID]">
                        <div class="thumbnail">
                            <a class="image-link">
                                <xsl:attribute name="href">
                                    <xsl:value-of select="mets:FLocat[@LOCTYPE='URL']/@xlink:href" />
                                </xsl:attribute>
                                <img class="img-thumbnail" alt="Thumbnail">
                                    <xsl:attribute name="src">
                                        <xsl:value-of select="$context/mets:fileSec/mets:fileGrp[@USE='THUMBNAIL']/mets:file[@GROUPID=current()/@GROUPID]/mets:FLocat[@LOCTYPE='URL']/@xlink:href" />
                                    </xsl:attribute>
                                </img>
                            </a>
                        </div>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:call-template name="getFileIcon">
                            <xsl:with-param name="mimetype">
                                <xsl:value-of select="substring-after(@MIMETYPE,'/')" />
                            </xsl:with-param>
                            <xsl:with-param name="showLarge">
                                <xsl:text>true</xsl:text>
                            </xsl:with-param>
                        </xsl:call-template>
                    </xsl:otherwise>
                </xsl:choose>
            </div>
            <div>
                <xsl:attribute name="class">
                    <xsl:value-of select="$contentBlockClasses" />
                </xsl:attribute>
                <dl class="file-metadata dl-horizontal">
                    <dt>
                        <i18n:text>xmlui.dri2xhtml.METS-1.0.item-files-name</i18n:text>
                        <xsl:text>:</xsl:text>
                    </dt>
                    <dd class="word-break">
                        <xsl:attribute name="title">
                            <xsl:value-of select="mets:FLocat[@LOCTYPE='URL']/@xlink:title" />
                        </xsl:attribute>
                        <a>
                            <xsl:attribute name="href">
                                <xsl:value-of select="mets:FLocat[@LOCTYPE='URL']/@xlink:href" />
                            </xsl:attribute>
                            <xsl:attribute name="class">
                                <xsl:text>pdf-file</xsl:text>
                            </xsl:attribute>
                            <xsl:attribute name="aria-label">
                                <xsl:choose>
                                    <xsl:when test="contains(mets:FLocat[@LOCTYPE='URL']/@xlink:href,'isAllowed=n')">
                                        <xsl:text>Access to this file is restricted.</xsl:text>
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <xsl:text>Download </xsl:text>
                                        <xsl:value-of select="mets:FLocat[@LOCTYPE='URL']/@xlink:title" />
                                    </xsl:otherwise>
                                </xsl:choose>
                            </xsl:attribute>
                            <xsl:value-of select="mets:FLocat[@LOCTYPE='URL']/@xlink:title" />
                            <xsl:choose>
                                <xsl:when test="contains(mets:FLocat[@LOCTYPE='URL']/@xlink:href,'isAllowed=n')">
                                    &#160;<i aria-hidden="true" class="fas fa-lock hidden-print hidden-md hidden-lg"></i>
                                </xsl:when>
                                <xsl:otherwise>
                                    &#160;<i aria-hidden="true" class="far fa-file-pdf hidden-print hidden-md hidden-lg"></i>
                                </xsl:otherwise>
                            </xsl:choose>
                        </a>
                    </dd>
                    <!-- File size always comes in bytes and thus needs conversion -->
                    <dt>
                        <i18n:text>xmlui.dri2xhtml.METS-1.0.item-files-size</i18n:text>
                        <xsl:text>:</xsl:text>
                    </dt>
                    <dd class="word-break">
                        <xsl:choose>
                            <xsl:when test="@SIZE &lt; 1024">
                                <xsl:value-of select="@SIZE" />
                                <i18n:text>xmlui.dri2xhtml.METS-1.0.size-bytes</i18n:text>
                            </xsl:when>
                            <xsl:when test="@SIZE &lt; 1024 * 1024">
                                <xsl:value-of select="substring(string(@SIZE div 1024),1,5)" />
                                <i18n:text>xmlui.dri2xhtml.METS-1.0.size-kilobytes</i18n:text>
                            </xsl:when>
                            <xsl:when test="@SIZE &lt; 1024 * 1024 * 1024">
                                <xsl:value-of select="substring(string(@SIZE div (1024 * 1024)),1,5)" />
                                <i18n:text>xmlui.dri2xhtml.METS-1.0.size-megabytes</i18n:text>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:value-of select="substring(string(@SIZE div (1024 * 1024 * 1024)),1,5)" />
                                <i18n:text>xmlui.dri2xhtml.METS-1.0.size-gigabytes</i18n:text>
                            </xsl:otherwise>
                        </xsl:choose>
                    </dd>
                    <!--
                    Lookup File Type description in local messages.xml based on MIME Type.
                    In the original DSpace, this would get resolved to an application via
                    the Bitstream Registry, but we are constrained by the capabilities of METS
                    and can't really pass that info through.
                    -->
                    <xsl:if test="$repoName = 'AUrora' or $repoName = 'DeepSpace'">
                        <dt>
                            <i18n:text>xmlui.dri2xhtml.METS-1.0.item-files-format</i18n:text>
                            <xsl:text>:</xsl:text>
                        </dt>
                        <dd class="word-break">
                            <xsl:call-template name="getFileTypeDesc">
                                <xsl:with-param name="mimetype">
                                    <xsl:value-of select="substring-before(@MIMETYPE,'/')" />
                                    <xsl:text>/</xsl:text>
                                    <xsl:choose>
                                        <xsl:when test="contains(@MIMETYPE,';')">
                                            <xsl:value-of select="substring-before(substring-after(@MIMETYPE,'/'),';')" />
                                        </xsl:when>
                                        <xsl:otherwise>
                                            <xsl:value-of select="substring-after(@MIMETYPE,'/')" />
                                        </xsl:otherwise>
                                    </xsl:choose>
                                </xsl:with-param>
                            </xsl:call-template>
                        </dd>
                        <!-- Display the contents of 'Description' only if bitstream contains a description -->
                        <xsl:if test="mets:FLocat[@LOCTYPE='URL']/@xlink:label != ''">
                            <dt>
                                <i18n:text>xmlui.dri2xhtml.METS-1.0.item-files-description</i18n:text>
                                <xsl:text>:</xsl:text>
                            </dt>
                            <dd class="word-break">
                                <xsl:attribute name="title">
                                    <xsl:value-of select="mets:FLocat[@LOCTYPE='URL']/@xlink:label" />
                                </xsl:attribute>
                                <xsl:value-of select="util:shortenString(mets:FLocat[@LOCTYPE='URL']/@xlink:label, 30, 5)" />
                            </dd>
                        </xsl:if>
                    </xsl:if>
                </dl>
            </div>
        </div>
    </xsl:template>

    <xsl:template name="view-open">
        <a>
            <xsl:attribute name="href">
                <xsl:value-of select="mets:FLocat[@LOCTYPE='URL']/@xlink:href" />
            </xsl:attribute>
            <xsl:call-template name="getFileIcon" />
        </a>
    </xsl:template>

    <xsl:template name="display-rights">
        <xsl:variable name="file_id" select="jstring:replaceAll(jstring:replaceAll(string(@ADMID), '_METSRIGHTS', ''), 'rightsMD_', '')" />
        <xsl:variable name="rights_declaration" select="../../../mets:amdSec/mets:rightsMD[@ID = concat('rightsMD_', $file_id, '_METSRIGHTS')]/mets:mdWrap/mets:xmlData/rights:RightsDeclarationMD" />
        <xsl:variable name="rights_context" select="$rights_declaration/rights:Context" />
        <xsl:variable name="users">
            <xsl:for-each select="$rights_declaration/*">
                <xsl:value-of select="rights:UserName" />
                <xsl:choose>
                    <xsl:when test="rights:UserName/@USERTYPE = 'GROUP'">
                        <xsl:text> (group)</xsl:text>
                    </xsl:when>
                    <xsl:when test="rights:UserName/@USERTYPE = 'INDIVIDUAL'">
                        <xsl:text> (individual)</xsl:text>
                    </xsl:when>
                </xsl:choose>
                <xsl:if test="position() != last()">, </xsl:if>
            </xsl:for-each>
        </xsl:variable>

        <xsl:choose>
            <xsl:when test="not ($rights_context/@CONTEXTCLASS = 'GENERAL PUBLIC') and ($rights_context/rights:Permissions/@DISPLAY = 'true')">
                <a href="{mets:FLocat[@LOCTYPE='URL']/@xlink:href}">
                    <img width="64" height="64" src="{concat($theme-path,'/images/Crystal_Clear_action_lock3_64px.png')}" title="Read access available for {$users}" />
                    <!-- icon source: http://commons.wikimedia.org/wiki/File:Crystal_Clear_action_lock3.png -->
                </a>
            </xsl:when>
            <xsl:otherwise>
                <xsl:call-template name="view-open" />
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="getFileIcon">
        <xsl:param name="mimetype" />
        <xsl:param name="showLarge" />
        <i aria-hidden="true">
            <xsl:attribute name="class">
                <xsl:choose>
                    <xsl:when test="contains(mets:FLocat[@LOCTYPE='URL']/@xlink:href,'isAllowed=n')">
                        <xsl:text>fas fa-lock</xsl:text>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:choose>
                            <xsl:when test="contains($mimetype, 'audio')">
                                <xsl:text>far fa-file-audio</xsl:text>
                            </xsl:when>
                            <xsl:when test="contains($mimetype, 'vnd.ms-excel') or contains($mimetype, 'vnd.openxmlformats-officedocument.spreadsheetml.sheet')">
                                <xsl:text>far fa-file-excel</xsl:text>
                            </xsl:when>
                            <xsl:when test="contains($mimetype, 'html') or $mimetype = 'text/xml' or $mimetype = 'text/css'">
                                <xsl:text>far fa-file-code</xsl:text>
                            </xsl:when>
                            <xsl:when test="contains($mimetype, 'image')">
                                <xsl:text>far fa-file-image</xsl:text>
                            </xsl:when>
                            <xsl:when test="contains($mimetype, 'msword') or contains($mimetype, 'vnd.openxmlformats-officedocument.wordprocessingml.document')">
                                <xsl:text>far fa-file-word</xsl:text>
                            </xsl:when>
                            <xsl:when test="contains($mimetype, 'pdf')">
                                <xsl:text>far fa-file-pdf</xsl:text>
                            </xsl:when>
                            <xsl:when test="contains($mimetype, 'vnd.ms-powerpoint') or contains($mimetype, 'vnd.openxmlformats-officedocument.presentationml.presentation')">
                                <xsl:text>far fa-file-powerpoint</xsl:text>
                            </xsl:when>
                            <xsl:when test="contains($mimetype, 'video')">
                                <xsl:text>far fa-file-video</xsl:text>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:text>far fa-file</xsl:text>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:otherwise>
                </xsl:choose>
                <xsl:if test="$showLarge = 'true'">
                    <xsl:text> fa-3x</xsl:text>
                </xsl:if>
            </xsl:attribute>
        </i>
        <xsl:text></xsl:text>
    </xsl:template>

    <!-- Generate the license information from the file section -->
    <xsl:template match="mets:fileGrp[@USE='CC-LICENSE']" mode="simple">
        <li>
            <a href="{mets:file/mets:FLocat[@xlink:title='license_text']/@xlink:href}">
                <i18n:text>xmlui.dri2xhtml.structural.link_cc</i18n:text>
            </a>
        </li>
    </xsl:template>

    <!-- Generate the license information from the file section -->
    <xsl:template match="mets:fileGrp[@USE='LICENSE']" mode="simple">
        <li>
            <a href="{mets:file/mets:FLocat[@xlink:title='license.txt']/@xlink:href}">
                <i18n:text>xmlui.dri2xhtml.structural.link_original_license</i18n:text>
            </a>
        </li>
    </xsl:template>

    <!--
    File Type Mapping template

    This maps format MIME Types to human friendly File Type descriptions.
    Essentially, it looks for a corresponding 'key' in your messages.xml of this
    format: xmlui.dri2xhtml.mimetype.{MIME Type}

    (e.g.) <message key="xmlui.dri2xhtml.mimetype.application/pdf">PDF</message>

    If a key is found, the translated value is displayed as the File Type (e.g. PDF)
    If a key is NOT found, the MIME Type is displayed by default (e.g. application/pdf)
    -->
    <xsl:template name="getFileTypeDesc">
        <xsl:param name="mimetype" />

        <!--Build full key name for MIME type (format: xmlui.dri2xhtml.mimetype.{MIME type})-->
        <xsl:variable name="mimetype-key">xmlui.dri2xhtml.mimetype.<xsl:value-of select='$mimetype' />
        </xsl:variable>

        <!--Lookup the MIME Type's key in messages.xml language file.  If not found, just display MIME Type-->
        <i18n:text i18n:key="{$mimetype-key}">
            <xsl:value-of select="$mimetype" />
        </i18n:text>
    </xsl:template>

    <xsl:template name="formatDate">
        <xsl:param name="tempDate" />
        <xsl:variable name="str1" select="substring-before($tempDate,'-')" />
        <xsl:variable name="str2" select="substring-before(substring-after($tempDate,'-'), '-')" />
        <xsl:variable name="str3" select="substring-after(substring-after($tempDate,'-'), '-')" />
        <xsl:choose>
            <xsl:when test="string-length($str1) = 4">
                <xsl:variable name="year" select="$str1" />
                <xsl:variable name="month" select="$str2" />
                <xsl:variable name="day" select="$str3" />
                <xsl:value-of select="concat($month, '-', $day, '-', $year)" />
            </xsl:when>
            <xsl:when test="string-length($str3) = 4">
                <xsl:variable name="year" select="$str3" />
                <xsl:variable name="month" select="$str1" />
                <xsl:variable name="day" select="$str2" />
                <xsl:value-of select="concat($month, '-', $day, '-', $year)" />
            </xsl:when>
        </xsl:choose>
    </xsl:template>

</xsl:stylesheet>
