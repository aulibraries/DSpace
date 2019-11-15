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

    <xsl:template match="dri:div[contains(@id, 'summary')]">
        <xsl:apply-templates select="dri:head" />
        <div class="col-lg-12" style="padding-bottom: 20px;">
            <xsl:apply-templates select="dri:div[contains(@id, 'Row')]" mode="summaryRow"/>
            <div class="row">
                <xsl:apply-templates select="dri:p" />
            </div>
        </div>

    </xsl:template>
    <xsl:template match="dri:div[contains(@id, 'sizeRow')]" mode="summaryRow">
        <xsl:variable name="sizeInfo">
            <xsl:copy-of select="dri:p/node()"/>
        </xsl:variable>
        <div class="row">
            <p>
                <xsl:apply-templates select="dri:head" mode="rowHead"/>
                <xsl:text>  </xsl:text>
                <xsl:choose>
                    <xsl:when test="$sizeInfo &lt; 1024">
                        <xsl:value-of select="$sizeInfo"/>
                        <i18n:text>xmlui.dri2xhtml.METS-1.0.size-bytes</i18n:text>
                    </xsl:when>
                    <xsl:when test="$sizeInfo &lt; 1024 * 1024">
                        <xsl:value-of select="substring(string($sizeInfo div 1024),1,5)"/>
                        <i18n:text>xmlui.dri2xhtml.METS-1.0.size-kilobytes</i18n:text>
                    </xsl:when>
                    <xsl:when test="$sizeInfo &lt; 1024 * 1024 * 1024">
                        <xsl:value-of select="substring(string($sizeInfo div (1024 * 1024)),1,5)"/>
                        <i18n:text>xmlui.dri2xhtml.METS-1.0.size-megabytes</i18n:text>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="substring(string($sizeInfo div (1024 * 1024 * 1024)),1,5)"/>
                        <i18n:text>xmlui.dri2xhtml.METS-1.0.size-gigabytes</i18n:text>
                    </xsl:otherwise>
                </xsl:choose>
            </p>
        </div>
    </xsl:template>

    <xsl:template match="dri:div" mode="summaryRow">
        <div class="row">
            <p><xsl:apply-templates select="dri:head" mode="rowHead"/><xsl:text>  </xsl:text><xsl:copy-of select="dri:p/node()"/></p>
        </div>
    </xsl:template>
    <xsl:template match="dri:head" mode="rowHead">
        <b><xsl:copy-of select="node()" /><xsl:text>: </xsl:text></b>
    </xsl:template>
</xsl:stylesheet>