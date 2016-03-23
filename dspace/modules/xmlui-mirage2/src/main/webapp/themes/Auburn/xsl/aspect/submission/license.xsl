<?xml version="1.0" encoding="UTF-8"?>

<!--
    Document   : license.xsl
    Created on : November 5, 2014, 8:21 AM
    Author     : STONEMA
    Description:
        Purpose of transformation follows.
-->
<xsl:stylesheet
	xmlns:i18n="http://apache.org/cocoon/i18n/2.1"
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
	xmlns:confman="org.dspace.core.ConfigurationManager"
	exclude-result-prefixes="xalan encoder i18n dri mets dim xlink xsl util jstring rights confman">
	<xsl:output indent="yes"/>
	
	<xsl:template match="dri:div[@id='aspect.submission.StepTransformer.div.submit-license-inner']/dri:p">
		<xsl:apply-templates />
	</xsl:template>
	<xsl:template match="dri:div[@id='aspect.submission.StepTransformer.div.submit-license-standard-text']">
		<p><strong>PLEASE READ: Important information about restricting access to your ETD</strong></p>
		<p>AUETD is an online database of electronic theses and dissertations (ETDs) submitted by Auburn University graduate students in partial 
		fulfillment of the university's graduate degree requirements. Its primary purpose is to make Auburn University ETDs easy to find and read 
		online. However, the University does offer graduate students the option of restricting access to the <strong>full text</strong> of their ETD <strong>for a limited 
		period of time</strong> (this is also known as an "embargo"). Graduate students who wish to restrict access temporarily to the <strong>full text</strong> of their 
		ETD may do so by selecting one of the embargo options <strong>during the ETD submission process</strong>. Students who choose to exercise this option should 
		be aware that basic bibliographic information about their ETD will appear in the AUETD database and that the full text of their ETD will 
		become publicly available after the embargo period they selected has expired. They should also be aware that basic bibliographic information 
		about all the ETDs in the AUETD database is indexed by Google and other Internet search engines and may appear in search results for those 
		search engines.</p>
		<p>Students who have questions or concerns about this policy are encouraged to contact <a href="mailto:jcl0014@auburn.edu">Clint Lovelace</a>  at 334-844-4112 before submitting their 
		ETD to AUETD. The Graduate School will not retroactively place embargoes on ETDs, so it is important to select the correct option during the 
		submission process.</p>
	</xsl:template>
	
</xsl:stylesheet>
