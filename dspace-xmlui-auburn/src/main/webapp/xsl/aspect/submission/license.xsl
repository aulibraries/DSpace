<?xml version="1.0" encoding="UTF-8"?>
<!--

    The contents of this file are subject to the license and copyright
    detailed in the LICENSE and NOTICE files at the root of the source
    tree and available online at

    http://www.dspace.org/license/

-->
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
		<p><strong>PLEASE READ: Important information about restricting access to your ETD and the ProQuest dissemination agreement.  The Graduate School is 
            unable to retroactively place embargoes on ETDs.</strong></p>
        <h2 class="h3">EMBARGO OPTIONS</h2>
		<p>AUETD is an online database of electronic theses and dissertations (ETDs) submitted by Auburn University graduate students in partial fulfillment 
            of the graduate degree requirements.  Graduate students can request an embargo, or delayed release, which restricts access to the full text of 
            their ETD for a limited period of time.  Graduate students who wish to restrict access temporarily of their ETD full text may do so by selecting 
            one of the embargo options during the ETD submission process. Students who choose to exercise this option should be aware that basic bibliographic 
            information about their ETD will appear in the AUETD database and that the full text of their ETD will become publicly available after the embargo 
            period has expired.  Basic bibliographic information about all ETDs in the AUETD database is indexed by Google and other Internet search engines 
            and may appear in search results for those search engines.  <strong>Please select the correct embargo option during the submission process as the 
            Graduate School is unable to retroactively place embargoes on ETDs.</strong></p>
        <h2 class="h3">DISSEMINATION AGREEMENT</h2>
        <p>Auburn University disseminates its scholarly output of master’s theses and dissertations through ProQuest Dissertation and Theses Global in order 
            to promote and preserve the intellectual output of its master’s and doctoral degree candidates.  Embargo options are recognized and enforced.  
            ProQuest&#169; Dissertation &amp; Theses Global reaches 3,000 universities with over 200 million searches annually and supports discovery of 
            scholarly output through all major subject and discipline indexes, such as SciFinder, MathSciNet, PsycINFO, and ERIC.  ProQuest provides these 
            services at no charge and facilitates the nonexclusive distribution of master's thesis' or doctoral dissertations.  Royalties could be received 
            based on sales and usage of the thesis or dissertation through ProQuest.</p>
        <h2 class="h3">QUESTIONS?</h2>
		<p>If there are questions or concerns about the embargo options, please contact the Graduate School at etdhelp@auburn.edu.  If there are questions 
            about the ProQuest dissemination agreement, please contact <a href="mailto:disspub@proquest.com">disspub@proquest.com</a> or see link for 
            ProQuest FAQ: <a href="https://www.proquest.com/products-services/dissertations/proquest-dissertations-faq.html" alt="Find answers to your questions about ProQuest at ProQuest FAQ" rel="noreferrer noopener" target="_blank">https://www.proquest.com/products-services/dissertations/proquest-dissertations-faq.html</a></p>
	</xsl:template>
	
</xsl:stylesheet>
