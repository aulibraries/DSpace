<!--

    The contents of this file are subject to the license and copyright
    detailed in the LICENSE and NOTICE files at the root of the source
    tree and available online at

    http://www.dspace.org/license/

-->

<!--
    Rendering specific to the navigation (options)

    Author: art.lowel at atmire.com
    Author: lieven.droogmans at atmire.com
    Author: ben at atmire.com
    Author: Alexey Maslov

-->

<xsl:stylesheet xmlns:i18n="http://apache.org/cocoon/i18n/2.1" 
    xmlns:dri="http://di.tamu.edu/DRI/1.0/" 
    xmlns:mets="http://www.loc.gov/METS/" 
    xmlns:xlink="http://www.w3.org/TR/xlink/" 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0" 
    xmlns:dim="http://www.dspace.org/xmlns/dspace/dim" 
    xmlns:xhtml="http://www.w3.org/1999/xhtml" 
    xmlns:mods="http://www.loc.gov/mods/v3" 
    xmlns:dc="http://purl.org/dc/elements/1.1/" 
    xmlns="http://www.w3.org/1999/xhtml" exclude-result-prefixes="i18n dri mets xlink xsl dim xhtml mods dc">

    <xsl:output indent="yes" />

    <xsl:variable name="serverName" select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@qualifier='serverName']"/>
    <xsl:variable name="contextPath" select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element = 'contextPath']"/>
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

    <!--
        The template to handle dri:options. Since it contains only dri:list tags (which carry the actual
        information), the only things than need to be done is creating the ds-options div and applying
        the templates inside it.

        In fact, the only bit of real work this template does is add the search box, which has to be
        handled specially in that it is not actually included in the options div, and is instead built
        from metadata available under pageMeta.
    -->
    <!-- TODO: figure out why i18n tags break the go button -->
    <xsl:template match="dri:options">
        <div class="word-break hidden-print sidebar-content" id="ds-options">
            <xsl:apply-templates />
            <!-- DS-984 Add RSS Links to Options Box -->
            <xsl:if test="count(/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='feed']) != 0">
                <div>
                    <h2 class="ds-option-set-head h5">
                        <i18n:text>xmlui.feed.header</i18n:text>
                    </h2>
                    <div id="ds-feed-option" class="ds-option-set list-group col-sm-12">
                        <xsl:call-template name="addRSSLinks" />
                    </div>
                </div>
            </xsl:if>
        </div>
    </xsl:template>

    <!-- Add each RSS feed from meta to a list -->
    <xsl:template name="addRSSLinks">
        <xsl:for-each select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='feed']">
            <a class="list-group-item">
                <xsl:attribute name="href">
                    <xsl:value-of select="." />
                </xsl:attribute>

                <img src="{concat($context-path, '/static/icons/feed.png')}" class="btn-xs" alt="xmlui.mirage2.navigation.rss.feed" i18n:attr="alt" />

                <xsl:choose>
                    <xsl:when test="contains(., 'rss_1.0')">
                        <xsl:text>RSS 1.0</xsl:text>
                    </xsl:when>
                    <xsl:when test="contains(., 'rss_2.0')">
                        <xsl:text>RSS 2.0</xsl:text>
                    </xsl:when>
                    <xsl:when test="contains(., 'atom_1.0')">
                        <xsl:text>Atom</xsl:text>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="@qualifier" />
                    </xsl:otherwise>
                </xsl:choose>
            </a>
        </xsl:for-each>
    </xsl:template>

    <xsl:template match="dri:options//dri:list">
        <xsl:apply-templates select="dri:head" mode="groupItemHeading" />
        <xsl:apply-templates select="dri:item" />
        <xsl:apply-templates select="dri:list" />
    </xsl:template>

    <!-- Prevent the the current context (ie community or collection) menu option items from appearing. -->
    <xsl:template match="dri:list[@id='aspect.browseArtifacts.Navigation.list.context']">
        <xsl:choose>
            <xsl:when test="$repoName = 'AUrora' or $repoName = 'DeepSpace'">
                <xsl:apply-templates select="dri:head" mode="groupItemHeading" />
                <xsl:apply-templates select="dri:item" />
                <xsl:apply-templates select="dri:list" />
            </xsl:when>
            <xsl:otherwise />
        </xsl:choose>
    </xsl:template>

    <xsl:template match="dri:options/dri:list[not(@n='account')]" priority="3">
        <xsl:if test="count(child::node()) &gt; 0">
            <xsl:apply-templates select="dri:head" mode="optionHead" />
            <div>
                <xsl:call-template name="standardAttributes">
                    <xsl:with-param name="class">ds-option-set list-group col-sm-12</xsl:with-param>
                </xsl:call-template>
                <xsl:apply-templates select="dri:item" />
                <xsl:apply-templates select="dri:list" />
            </div>
        </xsl:if>
    </xsl:template>

    <xsl:template match="dri:options/dri:list[@n='account']" priority="1">
        <xsl:variable name="viewAccountNav">
            <xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='nav'][@qualifier='view-account-nav']" />
        </xsl:variable>
        <xsl:variable name="pageURI">
            <xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='request'][@qualifier='URI']" />
        </xsl:variable>

        <xsl:choose>
            <xsl:when test="not(contains($pageURI,'login')) and not(contains($pageURI, 'restricted-resource'))">
                <xsl:if test="count(child::node()) &gt; 0">
                    <xsl:apply-templates select="dri:head" mode="optionHead" />
                    <div>
                        <xsl:call-template name="standardAttributes">
                            <xsl:with-param name="class">list-group col-sm-12</xsl:with-param>
                        </xsl:call-template>
                        <xsl:apply-templates select="dri:item" />
                        <xsl:apply-templates select="dri:list" />
                    </div>
                </xsl:if>
            </xsl:when>
            <xsl:otherwise>
                <xsl:choose>
                    <xsl:when test="$viewAccountNav = 'true'">
                        <xsl:if test="count(child::node()) &gt; 0">
                            <xsl:apply-templates select="dri:head" mode="optionHead" />
                            <div>
                                <xsl:call-template name="standardAttributes">
                                    <xsl:with-param name="class">list-group col-sm-11</xsl:with-param>
                                </xsl:call-template>
                                <xsl:apply-templates select="dri:item" />
                                <xsl:apply-templates select="dri:list" />
                            </div>
                        </xsl:if>
                    </xsl:when>
                    <xsl:otherwise />
                </xsl:choose>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="dri:options//dri:item">
        <div>
            <xsl:call-template name="standardAttributes">
                <xsl:with-param name="class">list-group-item ds-option</xsl:with-param>
            </xsl:call-template>
            <xsl:apply-templates />
        </div>
    </xsl:template>

    <xsl:template match="dri:options//dri:item[dri:xref]">
        <xsl:choose>
            <xsl:when test="$repoName = 'AUETD'">
                <xsl:if test="not(contains(dri:xref/@target, 'community-list'))">
                    <a href="{dri:xref/@target}">
                        <xsl:call-template name="standardAttributes">
                            <xsl:with-param name="class">list-group-item ds-option</xsl:with-param>
                        </xsl:call-template>
                        <xsl:choose>
                            <xsl:when test="dri:xref/node()">
                                <xsl:apply-templates select="dri:xref/node()" />
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:value-of select="dri:xref" />
                            </xsl:otherwise>
                        </xsl:choose>
                    </a>
                </xsl:if>
            </xsl:when>
            <xsl:otherwise>
                <a href="{dri:xref/@target}">
                    <xsl:call-template name="standardAttributes">
                        <xsl:with-param name="class">list-group-item ds-option</xsl:with-param>
                    </xsl:call-template>
                    <xsl:choose>
                        <xsl:when test="dri:xref/node()">
                            <xsl:apply-templates select="dri:xref/node()" />
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:value-of select="dri:xref" />
                        </xsl:otherwise>
                    </xsl:choose>
                </a>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="dri:options/dri:list/dri:head" mode="optionHead" priority="1">
        <span class="block ds-option-set-head h4">
            <xsl:apply-templates />
        </span>
    </xsl:template>

    <xsl:template match="dri:options/dri:list//dri:list/dri:head" mode="groupItemHeading" priority="3">
        <div class="list-group-item active">
            <span>
                <xsl:call-template name="standardAttributes">
                    <xsl:with-param name="class">
                        <xsl:value-of select="@rend" />
                        <xsl:text> list-group-item-heading</xsl:text>
                    </xsl:with-param>
                </xsl:call-template>
                <xsl:apply-templates />
            </span>
        </div>
    </xsl:template>

    <xsl:template match="dri:list[count(child::*)=0]" />

</xsl:stylesheet>