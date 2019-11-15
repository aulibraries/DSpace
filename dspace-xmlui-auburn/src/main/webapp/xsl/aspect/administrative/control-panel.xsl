<xsl:stylesheet xmlns:i18n="http://apache.org/cocoon/i18n/2.1"
	xmlns:dri="http://di.tamu.edu/DRI/1.0/"
	xmlns:mets="http://www.loc.gov/METS/"
	xmlns:xlink="http://www.w3.org/TR/xlink/"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
	xmlns:dim="http://www.dspace.org/xmlns/dspace/dim"
	xmlns:xhtml="http://www.w3.org/1999/xhtml"
	xmlns:mods="http://www.loc.gov/mods/v3"
	xmlns:dc="http://purl.org/dc/elements/1.1/"
	xmlns="http://www.w3.org/1999/xhtml"
	exclude-result-prefixes="i18n dri mets xlink xsl dim xhtml mods dc">

    <xsl:output indent="yes"/>
	
    <xsl:template match="dri:list[@n='javaOs' or @n='runtime' or @n='cocoon' or @n='dspace' or @n='oai-generator-settings' or @n='oai-harvester-settings']">
        <xsl:if test="count(*[not(name()='head')]) > 0">
            <div>
                <xsl:call-template name="standardAttributes">
                    <xsl:with-param name="class">
                        <xsl:text>control-panel-list col-lg-12</xsl:text>
                    </xsl:with-param>
                </xsl:call-template>
                <xsl:apply-templates select="dri:head" />
                <dl class="dl-horizontal">
                    <xsl:apply-templates select="dri:item" mode="ControlPanelListItem" />
                </dl>
            </div>
        </xsl:if>
    </xsl:template>
    <xsl:template match="dri:list/dri:item" mode="ControlPanelListItem">
        <!--<div>
                <xsl:call-template name="standardAttributes">
                        <xsl:with-param name="class">
                                <xsl:text>row col-lg-12 cp-info-row</xsl:text>
                        </xsl:with-param>
                </xsl:call-template>
                <xsl:if test="name(preceding-sibling::*[position()=1]) = 'label'">
                        <xsl:apply-templates select="preceding-sibling::*[position()=1]" mode="labeled"/>
                </xsl:if>
                <div>
                        <xsl:call-template name="standardAttributes">
                                <xsl:with-param name="class">
                                                <xsl:choose>
                                                        <xsl:when test="child::dri:p/dri:field/@type='text'">
                                                                <xsl:text>col-sm-8</xsl:text>
                                                        </xsl:when>
                                                        <xsl:otherwise>
                                                                <xsl:text>col-sm-4</xsl:text>
                                                        </xsl:otherwise>
                                                </xsl:choose>
                                </xsl:with-param>
                        </xsl:call-template>
                        <xsl:apply-templates />
                </div>
                <dl class="dl-horizontal">
                        <xsl:if test="name(preceding-sibling::*[position()=1]) = 'label'">
                                <xsl:apply-templates select="preceding-sibling::*[position()=1]" mode="labeled"/>
                        </xsl:if>
                        <dd>
                                <xsl:apply-templates />
                        </dd>
                </dl>
        </div> -->
        <xsl:if test="name(preceding-sibling::*[position()=1]) = 'label'">
            <xsl:apply-templates select="preceding-sibling::*[position()=1]" mode="ControlPanelLabeling"/>
        </xsl:if>
        <dd>
            <xsl:apply-templates />
        </dd>
    </xsl:template>
    <xsl:template match="dri:list/dri:label" priority="2" mode="ControlPanelLabeling">
    <!-- <div>
            <xsl:call-template name="standardAttributes">
                    <xsl:with-param name="class">
                            <xsl:text>col-sm-4</xsl:text>
                    </xsl:with-param>
            </xsl:call-template>
            <xsl:if test="count(./node())>0">
                <span>
                    <xsl:attribute name="class">
                        <xsl:text>ds-gloss-list-label </xsl:text>
                        <xsl:value-of select="@rend"/>
                    </xsl:attribute>
                    <xsl:apply-templates />
                    <xsl:text>:&#160;</xsl:text>
                </span>
             </xsl:if>
        </div> -->
        <dt>
            <xsl:if test="count(./node())>0">
                <xsl:apply-templates />
            </xsl:if>
        </dt>
    </xsl:template>
</xsl:stylesheet>
