<!--

    The contents of this file are subject to the license and copyright
    detailed in the LICENSE and NOTICE files at the root of the source
    tree and available online at

    http://www.dspace.org/license/

-->

<!--
    Main structure of the page, determines where
    header, footer, body, navigation are structurally rendered.
    Rendering of the header, footer, trail and alerts

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
                xmlns:confman="org.dspace.core.ConfigurationManager"
                exclude-result-prefixes="i18n dri mets xlink xsl dim xhtml mods dc confman">

    <xsl:output method="xml" encoding="UTF-8" indent="yes"/>

    <!--
        Requested Page URI. Some functions may alter behavior of processing depending if URI matches a pattern.
        Specifically, adding a static page will need to override the DRI, to directly add content.
    -->
    <xsl:variable name="request-uri" select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='request'][@qualifier='URI']"/>
    <xsl:variable name="serverName" select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@qualifier='serverName']"/>
    <xsl:variable name="contextPath" select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element = 'contextPath']"/>
    <xsl:variable name="repoName">
        <xsl:choose>
            <xsl:when test="$serverName = 'etd.auburn.edu' or $contextPath = '/auetd'">
                <xsl:text>AUETD</xsl:text>
            </xsl:when>
            <xsl:when test="$serverName = 'aurora.auburn.edu' or $contextPath = '/aurora'">
                <xsl:text>AUrora</xsl:text>
            </xsl:when>
            <xsl:when test="$serverName = 'deepspace.lib.auburn.edu' or $contextPath = '/deepspace'">
                <xsl:text>DeepSpace</xsl:text>
            </xsl:when>
        </xsl:choose>
    </xsl:variable>
    <xsl:variable name="ctxPath">
        <xsl:choose>
            <xsl:when test="$contextPath != ''">
                <xsl:value-of select="$contextPath"/><xsl:text>/</xsl:text>
            </xsl:when>
            <xsl:otherwise>
                <xsl:text>/</xsl:text>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:variable>

    <!--
        The starting point of any XSL processing is matching the root element. In DRI the root element is document,
        which contains a version attribute and three top level elements: body, options, meta (in that order).

        This template creates the html document, giving it a head and body. A title and the CSS style reference
        are placed in the html head, while the body is further split into several divs. The top-level div
        directly under html body is called "ds-main". It is further subdivided into:
            "ds-header"  - the header div containing title, subtitle, trail and other front matter
            "ds-body"    - the div containing all the content of the page; built from the contents of dri:body
            "ds-options" - the div with all the navigation and actions; built from the contents of dri:options
            "ds-footer"  - optional footer div, containing misc information

        The order in which the top level divisions appear may have some impact on the design of CSS and the
        final appearance of the DSpace page. While the layout of the DRI schema does favor the above div
        arrangement, nothing is preventing the designer from changing them around or adding new ones by
        overriding the dri:document template.
    -->
    <xsl:template match="dri:document">

        <xsl:choose>
            <xsl:when test="not($isModal)">

            <xsl:text disable-output-escaping='yes'>&lt;!DOCTYPE html&gt;
            </xsl:text>
            <xsl:text disable-output-escaping="yes">&lt;html lang=&quot;en&quot;&gt;
            </xsl:text>

                <!-- First of all, build the HTML head element -->

                <xsl:call-template name="buildHead"/>

                <!-- Then proceed to the body -->
                <body class="headerlock headershrink">
                    <xsl:call-template name="buildAuburnGDPR"/>
                    <div id="top" role="navigation" aria-label="Main navigation and page content shortcuts">
                        <a class="skip" href="#au_navigation">Skip to Navigation</a>
                        <a class="skip" href="#page_content">Skip to Content</a>
                    </div>
                    <xsl:call-template name="buildHeader"/>
                    <div class="page_content_spacer"></div>
                    <div id="page_content" class="page_content">
                        <div class="content_row">
                            <div class="content_container">
                                <button id="sidebarToggle" class="btn btn-default hidden-print hidden-md hidden-lg" type="button" data-toggle="offcanvas">View Nav &gt;</button>
                            </div>
                        </div>
                        <div class="content_row row-offcanvas row-offcanvas-left">
                            <div class="content_container">
                                <div id="sidebar" class="col-xs-6 col-sm-3 sidebar-offcanvas">
                                    <xsl:apply-templates select="dri:options"/>
                                </div>
                                <div id="main-content" class="col-xs-12 col-sm-12 col-md-9 col-lg-9">
                                    <xsl:call-template name="buildTrail"/>
                                    <xsl:apply-templates select="*[not(self::dri:options)]"/>
                                </div>
                            </div>
                        </div>
                    </div>
                    <xsl:call-template name="buildFooter"/>
                    <xsl:call-template name="addJavascript"/>
                </body>
                <xsl:text disable-output-escaping="yes">&lt;/html&gt;</xsl:text>

            </xsl:when>
            <xsl:otherwise>
                <!-- This is only a starting point. If you want to use this feature you need to implement
                JavaScript code and a XSLT template by yourself. Currently this is used for the DSpace Value Lookup -->
                <xsl:apply-templates select="dri:body" mode="modal"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- The HTML head element contains references to CSS as well as embedded JavaScript code. Most of this
    information is either user-provided bits of post-processing (as in the case of the JavaScript), or
    references to stylesheets pulled directly from the pageMeta element. -->
    <xsl:template name="buildHead">
        <head>
            <meta charset="utf-8"/>
            <meta http-equiv="X-UA-Compatible" content="IE=edge"/>

            <meta name="viewport" content="width=device-width, initial-scale=1.0"/>

            <!-- Add the title in -->
            <xsl:variable name="page_title" select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='title'][last()]" />
            <title>
                <xsl:choose>
                    <xsl:when test="starts-with($request-uri, 'page/about')">
                        <i18n:text>xmlui.mirage2.page-structure.aboutThisRepository</i18n:text>
                    </xsl:when>
                    <xsl:when test="not($page_title)">
                        <xsl:text>  </xsl:text>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:copy-of select="$page_title/node()" />
                    </xsl:otherwise>
                </xsl:choose>
            </title>

            <meta name="Generator">
                <xsl:attribute name="content">
                    <xsl:text>DSpace</xsl:text>
                    <xsl:if test="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='dspace'][@qualifier='version']">
                        <xsl:text> </xsl:text>
                        <xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='dspace'][@qualifier='version']"/>
                    </xsl:if>
                </xsl:attribute>
            </meta>

            <!-- Add stylesheets -->
            <!-- BOOTSTRAP -->
            <link rel="stylesheet" href="https://cdn.auburn.edu/assets/css/bootstrap.min.css" integrity="sha384-BVYiiSIFeK1dGmJRAkycuHAHRg32OmUcww7on3RYdg4Va+PmSTsz/K68vbdEjh4u" crossorigin="anonymous"/>
            <!-- FONT AWESOME -->
            <link rel="stylesheet" href="https://use.fontawesome.com/releases/v5.6.3/css/all.css" integrity="sha384-UHRtZLI+pbxtHCWp1t77Bi1L4ZtiqrqD80Kn4Z8NTSRyMA2Fd33n5dQ8lWUE00s/" crossorigin="anonymous"/>
            <!-- AUBURN UNIVERSITY -->
            <link rel="stylesheet" href="https://cdn.auburn.edu/2016/_assets/css/global.min.css" integrity="sha384-DthvczyZCLEYneoTqIn/y5qcCXy0bnwLeJgEfAXQLoDQJnmY7PFy5t271cHWqAxc" crossorigin="anonymous"/>

            <link rel="stylesheet" href="https://cdn.datatables.net/1.10.18/css/jquery.dataTables.min.css" integrity="sha384-1UXhfqyOyO+W+XsGhiIFwwD3hsaHRz2XDGMle3b8bXPH5+cMsXVShDoHA3AH/y/p" crossorigin="anonymous"/>
            <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/select2/4.0.10/css/select2.min.css" integrity="sha384-KZO2FRYNmIHerhfYMjCIUaJeGBRXP7CN24SiNSG+wdDzgwvxWbl16wMVtWiJTcMt" crossorigin="anonymous"/>
            <link rel="stylesheet" href="https://code.jquery.com/ui/1.12.1/themes/base/jquery-ui.css" integrity="sha384-xewr6kSkq3dBbEtB6Z/3oFZmknWn7nHqhLVLrYgzEFRbU/DHSxW7K3B44yWUN60D" crossorigin="anonymous"/>
            <link rel="stylesheet" href="{concat($theme-path, 'styles/main.css')}"/>


            <!-- Favicons -->
            <link rel="apple-touch-icon-precomposed" sizes="144x144" href="https://cdn.auburn.edu/2016/_assets/ico/apple-touch-icon-144-precomposed.png"></link>
            <link rel="apple-touch-icon-precomposed" sizes="114x114" href="https://cdn.auburn.edu/2016/_assets/ico/apple-touch-icon-114-precomposed.png"></link>
            <link rel="apple-touch-icon-precomposed" sizes="72x72" href="https://cdn.auburn.edu/2016/_assets/ico/apple-touch-icon-72-precomposed.png"></link>
            <link rel="apple-touch-icon-precomposed" href="https://cdn.auburn.edu/2016/_assets/ico/apple-touch-icon-57-precomposed.png"></link>
            <link rel="shortcut icon" href="https://cdn.auburn.edu/2016/_assets/ico/favicon.png"></link>
            
            
            <!-- Add syndication feeds -->
            <xsl:for-each select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='feed']">
                <link rel="alternate" type="application">
                    <xsl:attribute name="type">
                        <xsl:text>application/</xsl:text>
                        <xsl:value-of select="@qualifier"/>
                    </xsl:attribute>
                    <xsl:attribute name="href">
                        <xsl:value-of select="."/>
                    </xsl:attribute>
                </link>
            </xsl:for-each>

            <!--  Add OpenSearch auto-discovery link -->
            <xsl:if test="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='opensearch'][@qualifier='shortName']">
                <link rel="search" type="application/opensearchdescription+xml">
                    <xsl:attribute name="href">
                        <xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='request'][@qualifier='scheme']"/>
                        <xsl:text>://</xsl:text>
                        <xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='request'][@qualifier='serverName']"/>
                        <xsl:text>:</xsl:text>
                        <xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='request'][@qualifier='serverPort']"/>
                        <xsl:value-of select="$context-path"/>
                        <xsl:text>/</xsl:text>
                        <xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='opensearch'][@qualifier='autolink']"/>
                    </xsl:attribute>
                    <xsl:attribute name="title" >
                        <xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='opensearch'][@qualifier='shortName']"/>
                    </xsl:attribute>
                </link>
            </xsl:if>

            <!-- The following javascript removes the default text of empty text areas when they are focused on or submitted -->
            <!-- There is also javascript to disable submitting a form when the 'enter' key is pressed. -->
            <script>
                //Clear default text of emty text areas on focus
                function tFocus(element)
                {
                if (element.value == '<i18n:text>xmlui.dri2xhtml.default.textarea.value</i18n:text>'){element.value='';}
                }
                //Clear default text of emty text areas on submit
                function tSubmit(form)
                {
                var defaultedElements = document.getElementsByTagName("textarea");
                for (var i=0; i != defaultedElements.length; i++){
                if (defaultedElements[i].value == '<i18n:text>xmlui.dri2xhtml.default.textarea.value</i18n:text>'){
                defaultedElements[i].value='';}}
                }
                //Disable pressing 'enter' key to submit a form (otherwise pressing 'enter' causes a submission to start over)
                function disableEnterKey(e)
                {
                var key;

                if(window.event)
                key = window.event.keyCode;     //Internet Explorer
                else
                key = e.which;     //Firefox and Netscape

                if(key == 13)  //if "Enter" pressed, then disable!
                return false;
                else
                return true;
                }
            </script>

            <!-- JAVA SCRIPTS -->
            <script src="https://use.typekit.net/xiy3wga.js"></script>
            <script>try {
                    Typekit.load({async: true});
                } catch (e) {
                }</script>

            <!-- HEADER SHRINKER -->
            <script src="https://cdn.auburn.edu/2016/_assets/js/classie.js"></script>
            <script src="https://cdn.auburn.edu/2016/_assets/js/header_shrinker.js"></script>

            <!-- Head metadata in item pages -->
            <xsl:if test="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='xhtml_head_item']">
                <xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='xhtml_head_item']" disable-output-escaping="yes"/>
            </xsl:if>

            <!-- Add all Google Scholar Metadata values -->
            <xsl:for-each select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[substring(@element, 1, 9) = 'citation_']">
                <meta name="{@element}" content="{.}"></meta>
            </xsl:for-each>
        </head>
    </xsl:template>


    <!-- The header (distinct from the HTML head element) contains the title, subtitle, login box and various
        placeholders for header images -->
    <xsl:template name="buildHeader">
        <header role="banner">
            <div class="pre_nav_container">
                <div class="content_container clearfix">

                    <div id="preheader_left" class="col-md-5 hidden-xs">
                        <ul class="nav nav-justified">
                            <li><a href="http://www.auburn.edu/main/currentstudents.html">Students</a></li>
                            <li><a href="http://www.aualum.org/">Alumni</a></li>
                            <li><a href="http://www.auburn.edu/student_info/student_affairs/AUPA/index.php">Parents</a></li>
                            <li><a href="http://www.auburn.edu/main/employees.html">Employees</a></li>
                            <li><a href="http://www.auburn.edu/administration/">Administration</a></li>
                        </ul>
                    </div> 

                    <div class="col-md-1 hidden-xs"></div>

                    <div id="preheader_right" class="col-md-6 col-xs-12">
                        <!-- START Desktop Items -->
                        <ul class="nav nav-justified collapse navbar-collapse">
                            <li class="pre_apply">
                                <a href="https://www.auburn.edu/admissions/attention-applicants.html">
                                    <span class="glyphicon glyphicon-ok"></span><span class="pre_nav_glyphlink">Apply Now</span>
                                </a>
                            </li>
                            <li class="pre_give">
                                <a href="https://www.auburn.edu/give">
                                    <span class="glyphicon glyphicon-gift"></span><span class="pre_nav_glyphlink">Give</span>
                                </a>
                            </li>
                            <li>
                                <a href="https://www.lib.auburn.edu/"><span class="glyphicon glyphicon-book"></span><span class="pre_nav_glyphlink">Libraries</span>
                                </a>
                            </li>
                            <li>
                                <a href="https://www.auburn.edu/map"><span class="glyphicon glyphicon-map-marker"></span><span class="pre_nav_glyphlink">Map</span>
                                </a>
                            </li>
                            <li>
                                <a href="https://auaccess.auburn.edu/"><span class="glyphicon glyphicon-phone"></span><span class="pre_nav_glyphlink">AU Access</span>
                                </a>
                            </li>
                            <li class="pre_search">
                                <a href="#" data-toggle="collapse" data-target="#au_searchpanel" aria-expanded="false"><span class="glyphicon glyphicon-search"><span class="sr-only">Toggle Search</span></span></a>
                            </li>
                        </ul>
                        <!-- END Desktop Items -->

                        <!-- START Mobile Items -->
                        <ul class="visible-xs clearfix preheader_right_mobile">
                            <li class="pre_apply">
                                <a href="https://www.auburn.edu/admissions/attention-applicants.html">
                                    <span class="glyphicon glyphicon-ok"></span><span class="pre_nav_glyphlink">Apply</span>
                                </a>
                            </li>
                            <li>
                                <a href="https://lib.auburn.edu/"><span class="glyphicon glyphicon-book"></span><span class="pre_nav_glyphlink">Libraries</span>
                                </a>
                            </li> 
                            <li class="pre_give">
                                <a href="https://www.auburn.edu/give">
                                    <span class="glyphicon glyphicon-gift"></span><span class="pre_nav_glyphlink">Give</span>
                                </a>
                            </li>
                            <li class="pre_auaccess">
                                <a href="https://auaccess.auburn.edu/">
                                    <span class="glyphicon glyphicon-phone"></span><span class="pre_nav_glyphlink">AU Access</span>
                                </a>
                            </li>    
                        </ul>

                        <div class="collapse" id="au_searchpanel">
                            <div class="visible-xs">
                                <ul class="visible-xs clearfix preheader_right_mobile2">
                                    <li>
                                        <a href="https://www.auburn.edu/map"><span class="glyphicon glyphicon-map-marker"></span><span class="pre_nav_glyphlink">Map</span>
                                        </a>
                                    </li>
                                    <li>
                                        <a href="https://www.auburn.edu/main/sitemap.php">
                                            <span class="glyphicon glyphicon-sort-by-alphabet"></span><span class="pre_nav_glyphlink">A-Z</span>
                                        </a>
                                    </li>
                                    <li>
                                        <a href="https://www.auburn.edu/main/auweb_campus_directory.html">
                                            <span class="glyphicon glyphicon-user"></span><span class="pre_nav_glyphlink">Find People</span>
                                        </a>
                                    </li>
                                </ul>         
                            </div>
                            <!-- END Mobile Items -->

                            <div class="pre_nav_search clearfix">
                                <div class="col-md-4">
                                    <a class="hidden-xs" href="http://www.auburn.edu/main/sitemap.php">A-Z</a> | 
                                    <a class="hidden-xs" href="http://www.auburn.edu/main/auweb_campus_directory.html">People Finder</a>
                                </div>

                                <div class="col-md-8 clearfix">
                                    <form action="https://search.auburn.edu" class="" method="get">
                                        <input type="text" name="q" aria-label="Auburn University Search" role="search" class="searchfield" placeholder="Search AU..." style="width:100%;"/>
                                        <input type="hidden" name="cx" value="006456623919840955604:pinevfah6qm"/>
                                        <input type="hidden" name="ie" value="utf-8"/>
                                    </form>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            <div class="header_logos_container">
                <div class="content_container clearfix">
                    <div class="header_logo col-xs-7 col-sm-5 col-md-4">
                        <a href="http://www.auburn.edu">
                            <img alt="Auburn University black and white logo" class="visible-print" src="https://cdn.auburn.edu/2016/_assets/images/auburn-logo-horizontal-bw.png" style="width:200px;"/>
                            <img alt="Auburn University logo" class="hidden-print" src="https://cdn.auburn.edu/2016/_assets/images/auburn-logo-horizontal.svg"/>
                        </a>
                    </div>
                    <div class="col-xs-5 col-sm-2 visible-xs hidden-print">
                        <div class="col-xs-6 mobile_button_menu">
                            <button aria-expanded="false" class="glyphicon glyphicon-menu-hamburger" data-target=".js-navbar-collapse" data-toggle="collapse" type="button">
                                <span class="sr-only">Toggle navigation</span>
                            </button>
                        </div>
                        <div class="col-xs-6 mobile_button_search">            
                            <button aria-expanded="false" class="glyphicon glyphicon-search" data-target="#au_searchpanel" data-toggle="collapse" type="button">
                                <span class="sr-only">Toggle Search Area</span>
                            </button>
                        </div>
                    </div>
                    <div class="col-md-2 col-sm-1"></div>
                    <div class="header_secondary col-xs-12 col-sm-6 col-md-6 hidden-print">
                        <img alt="This Is Auburn" class="header_title_image" src="https://cdn.auburn.edu/2016/_assets/images/thisisauburn_wide.svg"/> 
                        <span class="unit_title"><xsl:text>Electronic Theses and Dissertations</xsl:text></span>
                        <span class="unit_subtitle">
                            <xsl:if test="contains($serverName, 'localhost') or contains($serverName, 'dstest')">
                                <xsl:text>Development/Testing System</xsl:text>
                            </xsl:if>
                            <xsl:choose>
                                <xsl:when test="contains($serverName, 'localhost')">
                                    <xsl:text> (Local)</xsl:text>
                                </xsl:when>
                                <xsl:when test="contains($serverName, 'dstest')">
                                    <xsl:text> (DSTest)</xsl:text>
                                </xsl:when>
                                <xsl:otherwise></xsl:otherwise>
                            </xsl:choose>
                        </span>
                    </div>
                </div>   
            </div>
            <xsl:call-template name="buildHeaderNav"/>
        </header>
       
    </xsl:template>
	
    <xsl:template name="buildHeaderNav">
        <a href="#page_content" class="skip">Skip to Content</a>
        <div id="au_navigation" class="au_nav_container clearfix">
            <nav class="au_nav_links_container navbar navbar-default clearfix">
                <div class="collapse navbar-collapse js-navbar-collapse ">
                    <ul class="nav-justified dropdown mega-dropdown clearfix">
                        <li class="dropdown">
                            <a>
                                <xsl:attribute name="class">
                                    <xsl:text>dropdown-toggle</xsl:text>
                                </xsl:attribute>
                                <xsl:attribute name="href">
                                    <xsl:value-of select="$ctxPath"/>
                                </xsl:attribute>
                                <xsl:attribute name="title">
                                    <xsl:value-of select="$repoName"/><xsl:text> Home</xsl:text>
                                </xsl:attribute>
                                <xsl:value-of select="$repoName"/><xsl:text> Home</xsl:text>
                            </a>
                        </li>
                        <xsl:choose>
                            <xsl:when test="$repoName = 'AUETD'">
                                <li class="dropdown"><a class="dropdown-toggle" href="http://www.grad.auburn.edu/" title="The Auburn University Graduate School website will open in a new tab." rel="noreferrer noopener" target="_blank">Graduate School</a></li>
                            </xsl:when>
                            <xsl:otherwise>
                                <li class="dropdown">
                                    <a>
                                        <xsl:attribute name="href">
                                            <xsl:value-of select="$ctxPath"/><xsl:text>faqs.html</xsl:text>
                                        </xsl:attribute>
                                        <xsl:attribute name="class">
                                            <xsl:text>dropdown-toggle</xsl:text>
                                        </xsl:attribute>
                                        <xsl:attribute name="title">
                                            <xsl:text>FAQs</xsl:text>
                                        </xsl:attribute>
                                        <xsl:text>FAQs</xsl:text>
                                    </a>
                                </li>
                                <li class="dropdown">
                                    <a>
                                        <xsl:attribute name="class">
                                            <xsl:text>dropdown-toggle</xsl:text>
                                        </xsl:attribute>
                                        <xsl:attribute name="href">
                                            <xsl:value-of select="$ctxPath"/><xsl:text>liasions.html</xsl:text>
                                        </xsl:attribute>
                                        <xsl:attribute name="title">
                                            <xsl:text>Subject Librarians/Departmental Liaisons</xsl:text>
                                        </xsl:attribute>
                                        <xsl:text>Subject Librarians/Departmental Liaisons</xsl:text>
                                    </a>
                                </li>
                                <li class="dropdown">
                                    <a>
                                        <xsl:attribute name="class">
                                            <xsl:text>dropdown-toggle</xsl:text>
                                        </xsl:attribute>
                                        <xsl:attribute name="href">
                                            <xsl:value-of select="$ctxPath"/><xsl:text>contact.html</xsl:text>
                                        </xsl:attribute>
                                        <xsl:attribute name="title">
                                            <xsl:text>Contact</xsl:text>
                                        </xsl:attribute>
                                        <xsl:text>Contact</xsl:text>
                                    </a>
                                </li>
                            </xsl:otherwise>
                        </xsl:choose>
                    </ul>
                    <div class="nav nav-justified collapse in visible-xs"></div>
                </div>
            </nav>
        </div>
	</xsl:template>

    <!-- The header (distinct from the HTML head element) contains the title, subtitle, login box and various
        placeholders for header images -->
    <xsl:template name="buildTrail">
        <xsl:choose>
            <xsl:when test="count(/dri:document/dri:meta/dri:pageMeta/dri:trail) > 1">
                <ol class="breadcrumb">
                    <xsl:apply-templates select="/dri:document/dri:meta/dri:pageMeta/dri:trail[not(contains(@target, '123456789/1'))][not(contains(@target, '123456789/2'))][not(contains(@target, '10415/1'))][not(contains(@target, '10415/2'))]"/>
                </ol>
            </xsl:when>
            <xsl:otherwise>
                <ol class="breadcrumb">
                    <xsl:apply-templates select="/dri:document/dri:meta/dri:pageMeta/dri:trail[not(contains(@target, '123456789/1'))][not(contains(@target, '123456789/2'))][not(contains(@target, '10415/1'))][not(contains(@target, '10415/2'))]"/>
                </ol>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!--The Trail-->
    <xsl:template match="dri:trail">
        <!--put an arrow between the parts of the trail-->
        <li>
            <!-- Determine whether we are dealing with a link or plain text trail link -->
            <xsl:choose>
                <xsl:when test="./@target">
                    <a>
                        <xsl:attribute name="href">
                            <xsl:value-of select="./@target"/>
                        </xsl:attribute>
                        <xsl:apply-templates />
                    </a>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:attribute name="class">active</xsl:attribute>
                    <xsl:apply-templates />
                </xsl:otherwise>
            </xsl:choose>
        </li>
    </xsl:template>

    <xsl:template match="dri:trail" mode="dropdown">
        <!--put an arrow between the parts of the trail-->
        <li role="presentation">
            <!-- Determine whether we are dealing with a link or plain text trail link -->
            <xsl:choose>
                <xsl:when test="./@target">
                    <a role="menuitem">
                        <xsl:attribute name="href">
                            <xsl:value-of select="./@target"/>
                        </xsl:attribute>
                        <xsl:if test="position()=1">
                            <i class="glyphicon glyphicon-home" aria-hidden="true"/>&#160;
                        </xsl:if>
                        <xsl:apply-templates />
                    </a>
                </xsl:when>
                <xsl:when test="position() > 1 and position() = last()">
                    <xsl:attribute name="class">disabled</xsl:attribute>
                    <a role="menuitem" href="#">
                        <xsl:apply-templates />
                    </a>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:attribute name="class">active</xsl:attribute>
                    <xsl:if test="position()=1">
                        <i class="glyphicon glyphicon-home" aria-hidden="true"/>&#160;
                    </xsl:if>
                    <xsl:apply-templates />
                </xsl:otherwise>
            </xsl:choose>
        </li>
    </xsl:template>

    <!--The License-->
    <xsl:template name="cc-license">
        <xsl:param name="metadataURL"/>
        <xsl:variable name="externalMetadataURL">
            <xsl:text>cocoon:/</xsl:text>
            <xsl:value-of select="$metadataURL"/>
            <xsl:text>?sections=dmdSec,fileSec&amp;fileGrpTypes=THUMBNAIL</xsl:text>
        </xsl:variable>

        <xsl:variable name="ccLicenseName" select="document($externalMetadataURL)//dim:field[@element='rights']"/>
        <xsl:variable name="ccLicenseUri" select="document($externalMetadataURL)//dim:field[@element='rights'][@qualifier='uri']"/>
        <xsl:variable name="handleUri">
            <xsl:for-each select="document($externalMetadataURL)//dim:field[@element='identifier' and @qualifier='uri']">
                <a>
                    <xsl:attribute name="href">
                        <xsl:copy-of select="./node()"/>
                    </xsl:attribute>
                    <xsl:copy-of select="./node()"/>
                </a>
                <xsl:if test="count(following-sibling::dim:field[@element='identifier' and @qualifier='uri']) != 0">
                    <xsl:text>, </xsl:text>
                </xsl:if>
            </xsl:for-each>
        </xsl:variable>

        <xsl:if test="$ccLicenseName and $ccLicenseUri and contains($ccLicenseUri, 'creativecommons')">
            <div about="{$handleUri}" class="row">
            <div class="col-sm-3 col-xs-12">
                <a rel="license" href="{$ccLicenseUri}" alt="{$ccLicenseName}" title="{$ccLicenseName}">
                    <xsl:call-template name="cc-logo">
                        <xsl:with-param name="ccLicenseName" select="$ccLicenseName"/>
                        <xsl:with-param name="ccLicenseUri" select="$ccLicenseUri"/>
                    </xsl:call-template>
                </a>
            </div> 
            <div class="col-sm-8">
                <span>
                    <i18n:text>xmlui.dri2xhtml.METS-1.0.cc-license-text</i18n:text>
                    <xsl:value-of select="$ccLicenseName"/>
                </span>
            </div>
            </div>
        </xsl:if>
    </xsl:template>

    <xsl:template name="cc-logo">
        <xsl:param name="ccLicenseName"/>
        <xsl:param name="ccLicenseUri"/>
        <xsl:variable name="ccLogo">
             <xsl:choose>
                  <xsl:when test="starts-with($ccLicenseUri,
                                           'http://creativecommons.org/licenses/by/')">
                       <xsl:value-of select="'cc-by.png'" />
                  </xsl:when>
                  <xsl:when test="starts-with($ccLicenseUri,
                                           'http://creativecommons.org/licenses/by-sa/')">
                       <xsl:value-of select="'cc-by-sa.png'" />
                  </xsl:when>
                  <xsl:when test="starts-with($ccLicenseUri,
                                           'http://creativecommons.org/licenses/by-nd/')">
                       <xsl:value-of select="'cc-by-nd.png'" />
                  </xsl:when>
                  <xsl:when test="starts-with($ccLicenseUri,
                                           'http://creativecommons.org/licenses/by-nc/')">
                       <xsl:value-of select="'cc-by-nc.png'" />
                  </xsl:when>
                  <xsl:when test="starts-with($ccLicenseUri,
                                           'http://creativecommons.org/licenses/by-nc-sa/')">
                       <xsl:value-of select="'cc-by-nc-sa.png'" />
                  </xsl:when>
                  <xsl:when test="starts-with($ccLicenseUri,
                                           'http://creativecommons.org/licenses/by-nc-nd/')">
                       <xsl:value-of select="'cc-by-nc-nd.png'" />
                  </xsl:when>
                  <xsl:when test="starts-with($ccLicenseUri,
                                           'http://creativecommons.org/publicdomain/zero/')">
                       <xsl:value-of select="'cc-zero.png'" />
                  </xsl:when>
                  <xsl:when test="starts-with($ccLicenseUri,
                                           'http://creativecommons.org/publicdomain/mark/')">
                       <xsl:value-of select="'cc-mark.png'" />
                  </xsl:when>
                  <xsl:otherwise>
                       <xsl:value-of select="'cc-generic.png'" />
                  </xsl:otherwise>
             </xsl:choose>
        </xsl:variable>
        <img class="img-responsive">
             <xsl:attribute name="src">
                <xsl:value-of select="concat($theme-path,'/images/creativecommons/', $ccLogo)"/>
             </xsl:attribute>
             <xsl:attribute name="alt">
                 <xsl:value-of select="$ccLicenseName"/>
             </xsl:attribute>
        </img>
    </xsl:template>

    <!-- Like the header, the footer contains various miscellaneous text, links, and image placeholders -->
    <xsl:template name="buildFooter">
        <footer>
            <div class="content_container clearfix">
                <div class="footer_col col-sm-4 col_1">
                    <div class="footer_col_logo">
                        <img src="//www.auburn.edu/template/2016/_assets/images/au_logos/brandingimage-02.png" alt=""/>
                    </div>
                </div>
                <div class="footer_col col-sm-4 col_2">
                    <div class="col-xs-6">
                        <div class="h4">Contact</div>
                        <a href="https://www.google.com/maps/place/Samford+Hall,+182+S+College+St,+Auburn+University,+AL+36849/@32.6047401,-85.4849814,17z/data=!3m1!4b1!4m5!3m4!1s0x888cf31cea067167:0xbe475215b40ce95f!8m2!3d32.6047401!4d-85.4827927" rel="noreferrer noopener" target="_blank">
                        Auburn University<br/>
                        Auburn, Alabama 36849</a><br/>
                        <a href="tel:3348444000">(334) 844-4000</a><br/>
                        <a href="http://www.auburn.edu/websitefeedback/" rel="noreferrer noopener" target="_blank">Website Feedback</a><br/>
                        <a href="mailto:webmaster@auburn.edu">Webmaster</a>
                    </div>
                    <div class="col-xs-6">
                        <div class="h4">Connect</div>
                        <a href="http://www.facebook.com/auburnu" title="Auburn University Facebook page" rel="noreferrer noopener" target="_blank">Facebook</a><br/>
                        <a href="http://twitter.com/auburnu" title="Auburn University Twitter" rel="noreferrer noopener" target="_blank">Twitter</a><br/>
                        <a href="https://instagram.com/auburnu" title="Auburn University Instagram" rel="noreferrer noopener" target="_blank">Instagram</a><br/>
                        <a href="http://www.youtube.com/AuburnUniversity" title="Auburn University YouTube channel" rel="noreferrer noopener" target="_blank">YouTube</a><br/>
                    </div>
                    <div style="clear:both;"></div>
                </div>                
                <div class="footer_col col-sm-4 col_3">
                    <br/><br/>                
                    <a href="http://www.auburn.edu/accessibility" rel="noreferrer noopener" target="_blank">Campus Accessibility</a><br/>
                    <a href="http://www.auburn.edu/privacy" rel="noreferrer noopener" target="_blank">Privacy Statement</a><br/>
                    <a href="http://www.auburn.edu/copyright" rel="noreferrer noopener" target="_blank">Copyright &#169; <script>date = new Date(); document.write(date.getFullYear());</script></a>
                </div>            
            </div>
            <div class="to-top"><a href="#top"><span class="sr-only">Back to Top</span></a></div>
        </footer>
    </xsl:template>

    <!--
        The meta, body, options elements; the three top-level elements in the schema
    -->
    <!--
        The template to handle the dri:body element. It simply creates the ds-body div and applies
        templates of the body's child elements (which consists entirely of dri:div tags).
    -->
    <xsl:template match="dri:body">
        <xsl:if test="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='alert'][@qualifier='message']">
            <div class="alert">
                <button type="button" class="close" data-dismiss="alert">&#215;</button>
                <xsl:copy-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='alert'][@qualifier='message']/node()"/>
            </div>
        </xsl:if>

        <!-- Check for the custom pages -->
        <xsl:choose>
            <xsl:when test="$request-uri=''">
                <xsl:apply-templates select="*[not(@id='file.news.div.news')][not(@id='aspect.artifactbrowser.CommunityBrowser.div.comunity-browser')][not(@id='aspect.discovery.SiteRecentSubmissions.div.site-home')]" />
                <xsl:choose>
                    <xsl:when test="$serverName = 'etd.auburn.edu' or $contextPath = '/auetd'">
                        <xsl:call-template name="newsfeed" />
                        <xsl:call-template name="FrontPageSearch"/>
                        <xsl:call-template name="specialMessage"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:apply-templates select="dri:div[@id='file.news.div.news']" />
                        <xsl:call-template name="FrontPageSearch"/>
                        <xsl:apply-templates select="dri:div[@id='aspect.artifactbrowser.CommunityBrowser.div.comunity-browser']"/>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:when>
            <xsl:otherwise>
                <xsl:apply-templates />
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
    
    <xsl:template name="specialMessage">
        <div class="row submissionMsgRow">
            <div class="col-lg-12">
                <h1 class="ds-div-head">Submitting Your Thesis or Dissertation</h1>
                <p>Auburn University students submit their thesis or dissertation through the AUETD system. To start this process, login by clicking on the <b>Login</b> link under the "My Account" section of the navigation menu.  Once you have logged in click the Submit An Electronic Thesis or Dissertation button below, read and accept the Embargo policy, and then follow the upload instructions.  For questions about this process or the status of your thesis or dissertation in AUETD, please contact the Graduate School at <a title="Email the Graduate School" href="mailto:etdhelp@auburn.edu">etdhelp@auburn.edu</a>.</p>
            </div>
        </div>
        <xsl:if test="/dri:document/dri:meta/dri:userMeta/@authenticated = 'yes' and /dri:document/dri:meta/dri:userMeta/dri:metadata[@element='identifier'][@qualifier='authorized-submitter'] = 'yes'">
            <div class="row submissionBttnRow">
                <div class="col-lg-12">
                    <xsl:choose>
                        <xsl:when test="$serverName = 'etd.auburn.edu'">
                            <p><a class="btn btn-default submissionBttn" href="/handle/10415/2/submit">Submit an Electronic Thesis or Dissertation</a></p>
                        </xsl:when>
                        <xsl:otherwise>
                            <p><a class="btn btn-default submissionBttn" href="/auetd/handle/123456789/2/submit">Submit an Electronic Thesis or Dissertation</a></p>
                        </xsl:otherwise>
                    </xsl:choose>
                </div>
            </div>
        </xsl:if>

    </xsl:template>
	
    <xsl:template name="FrontPageSearch">
        <div id="frontPageSrchBlock" class="row frontPageSrchBlock primary">
            <h1 class="frontPageSearchHeader">Search <xsl:value-of select="$repoName"/></h1>
            <form id="aspect_artifactbrowser_FrontPageSearch_div_front-page-search" method="post">
                <xsl:attribute name="action">
                    <xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='contextPath']"/>
                    <xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='search'][@qualifier='simpleURL']"/>
                </xsl:attribute>
                <div class="form-group">
                    <label class="sr-only" for="aspect_artifactbrowser_FrontPageSearch_field_query">Search <xsl:value-of select="$repoName"/></label>
                    <div class="col-sm-10 clearPadding">
                        <div class="input-group">
                            <input id="aspect_artifactbrowser_FrontPageSearch_field_query" class="ds-text-field form-control" name="query" type="text" placeholder="xmlui.general.search.placeholder.AUETD_placeholder" i18n:attr="placeholder" value=""/>
                            <span class="input-group-btn">
                                <button id="aspect_artifactbrowser_FrontPageSearch_field_submit" class="ds-button-field btn btn-default" name="submit" title="Click to submit the search form" type="submit">
                                    <span>Search</span>
                                </button>
                            </span>
                        </div>
                    </div>
                </div>
            </form>
        </div>
    </xsl:template>
	
    <xsl:template name="newsfeed">
        <h1>Welcome to AUETD</h1>
        <div class="row">
            <div id="file_news_div_news" class="col-lg-12">
                <p>Welcome to AUETD, Auburn University's database of Master's theses and Ph.D. dissertations. The database contains a PDF version of every thesis or dissertation successfully defended at Auburn since the Fall 2005 semester.</p>
                <p>Auburn University librarians are available to answer questions about searching the AUETD database via <a href="https://askalibrarian.auburn.edu/" alt="Ask a Librarian" target="_blank">Ask a Librarian</a>.</p>
            </div>
        </div>
    </xsl:template>

    <!-- Currently the dri:meta element is not parsed directly. Instead, parts of it are referenced from inside
        other elements (like reference). The blank template below ends the execution of the meta branch -->
    <xsl:template match="dri:meta">
    </xsl:template>

    <!-- Meta's children: userMeta, pageMeta, objectMeta and repositoryMeta may or may not have templates of
        their own. This depends on the meta template implementation, which currently does not go this deep.
    <xsl:template match="dri:userMeta" />
    <xsl:template match="dri:pageMeta" />
    <xsl:template match="dri:objectMeta" />
    <xsl:template match="dri:repositoryMeta" />
    -->

    <xsl:template name="addJavascript">

        <!--TODO concat & minify!-->

        <script>
            <xsl:text>if(!window.DSpace){window.DSpace={};}window.DSpace.context_path='</xsl:text><xsl:value-of select="$context-path"/><xsl:text>';window.DSpace.theme_path='</xsl:text><xsl:value-of select="$theme-path"/><xsl:text>';</xsl:text>
        </script>

        <!--inject scripts.html containing all the theme specific javascript references
        that can be minified and concatinated in to a single file or separate and untouched
        depending on whether or not the developer maven profile was active-->
        <xsl:variable name="scriptURL">
            <xsl:text>cocoon://themes/</xsl:text>
            <!--we can't use $theme-path, because that contains the context path,
            and cocoon:// urls don't need the context path-->
            <xsl:value-of select="$pagemeta/dri:metadata[@element='theme'][@qualifier='path']"/>
            <xsl:text>scripts-dist.xml</xsl:text>
        </xsl:variable>
        <!-- <xsl:for-each select="document($scriptURL)/scripts/script">
            <script src="{$theme-path}{@src}" type="text/javascript">&#160;</script>
        </xsl:for-each> -->
        
        <script src="https://code.jquery.com/jquery-3.4.1.min.js" integrity="sha384-vk5WoKIaW/vJyUAd9n/wmopsmNhiy+L2Z+SBxGYnUkunIxVxAv/UtMOhba/xskxh" crossorigin="anonymous"></script>
        <script src="https://stackpath.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js" integrity="sha384-Tc5IQib027qvyjSMfHjOMaLkfuWVxZxUPnCJA7l2mCWNIpG9mGCD8wGNIcPD7Txa" crossorigin="anonymous"></script>
        <script src="https://code.jquery.com/ui/1.12.1/jquery-ui.min.js" integrity="sha256-VazP97ZCwtekAsvgPBSUwPFKdrwD3unUfSGVYrahUqU=" crossorigin="anonymous"></script>
        <script src="https://cdn.datatables.net/1.10.19/js/jquery.dataTables.min.js" integrity="sha384-rgWRqC0OFPisxlUvl332tiM/qmaNxnlY46eksSZD84t+s2vZlqGeHrncwIRX7CGp" crossorigin="anonymous"></script>
        <script src="https://cdnjs.cloudflare.com/ajax/libs/holder/2.9.6/holder.min.js" integrity="sha384-bSAO0//FZ+kEkMUA+BQwV9+DKxRuFoPhjdZJZmjmoc3M66CVZE/uRacR/B8tBVl+" crossorigin="anonymous"></script>
        <script src="https://cdn.jsdelivr.net/npm/handlebars@4.5.1/dist/handlebars.js" integrity="sha256-RX33SPSioT0KIh0u3Z1Di8j8SnTKWWjWeFmC9DLvlNw=" crossorigin="anonymous"></script>
        <script src="https://cdnjs.cloudflare.com/ajax/libs/select2/4.0.10/js/select2.min.js" integrity="sha384-KjsJwMB03GQH/gvsBTpEONeOUBnRY/Dpp+iDzeS4BMQi2Ayl2emKLGuLr3gChQhv" crossorigin="anonymous"></script>
        <script src="{concat($theme-path, 'scripts/theme.js')}" type="text/javascript">&#160;</script>
        

        <!-- Add javascipt specified in DRI -->
        <xsl:for-each select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='javascript'][not(@qualifier)]">
            <script type="text/javascript">
                <xsl:attribute name="src">
                    <xsl:value-of select="$theme-path"/>
                    <xsl:value-of select="."/>
                </xsl:attribute>&#160;</script>
        </xsl:for-each>

        <!-- add "shared" javascript from static, path is relative to webapp root-->
        <xsl:for-each select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='javascript'][@qualifier='static']">
            <!--This is a dirty way of keeping the scriptaculous stuff from choice-support
            out of our theme without modifying the administrative and submission sitemaps.
            This is obviously not ideal, but adding those scripts in those sitemaps is far
            from ideal as well-->
            <xsl:choose>
                <xsl:when test="text() = 'static/js/choice-support.js'">
                    <script>
                        <xsl:attribute name="src">
                            <xsl:value-of select="$theme-path"/>
                            <xsl:text>js/choice-support.js</xsl:text>
                        </xsl:attribute>&#160;</script>
                </xsl:when>
                <xsl:when test="not(starts-with(text(), 'static/js/scriptaculous'))">
                    <script>
                        <xsl:attribute name="src">
                            <xsl:value-of
                                    select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='contextPath'][not(@qualifier)]"/>
                            <xsl:text>/</xsl:text>
                            <xsl:value-of select="."/>
                        </xsl:attribute>&#160;</script>
                </xsl:when>
            </xsl:choose>
        </xsl:for-each>

        <!-- add setup JS code if this is a choices lookup page -->
        <xsl:if test="dri:body/dri:div[@n='lookup']">
            <xsl:call-template name="choiceLookupPopUpSetup"/>
        </xsl:if>

        <!-- Add a google analytics script if the key is present -->
        <xsl:if test="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='google'][@qualifier='analytics']">
           <script>
               <xsl:text>
                   function GATC() {
                        (function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
                        (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
                        m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
                        })(window,document,'script','//www.google-analytics.com/analytics.js','ga');
                        
                        ga('create', 'UA-2228003-3', 'auto');  // AU Tracker
                        ga('send', 'pageview');
                        
                        ga('create', '</xsl:text><xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='google'][@qualifier='analytics']"/><xsl:text>', auto, '</xsl:text><xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='request'][@qualifier='serverName']"/><xsl:text>');'  // Your Tracker Code
                        ga('</xsl:text><xsl:value-of select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='request'][@qualifier='serverName']"/><xsl:text>.send', 'pageview');
                        
                    };
               </xsl:text>
           </script>
        </xsl:if>
    </xsl:template>

    <!--The Language Selection-->
    <xsl:template name="languageSelection">
        <xsl:if test="count(/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='page'][@qualifier='supportedLocale']) &gt; 1">
            <li id="ds-language-selection" class="dropdown">
                <xsl:variable name="active-locale" select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='page'][@qualifier='currentLocale']"/>
                <a id="language-dropdown-toggle" href="#" role="button" class="dropdown-toggle" data-toggle="dropdown">
                    <span class="hidden-xs">
                        <xsl:value-of
                                select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='supportedLocale'][@qualifier=$active-locale]"/>
                        <xsl:text>&#160;</xsl:text>
                        <b class="caret"/>
                    </span>
                </a>
                <ul class="dropdown-menu pull-right" role="menu" aria-labelledby="language-dropdown-toggle" data-no-collapse="true">
                    <xsl:for-each
                            select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='page'][@qualifier='supportedLocale']">
                        <xsl:variable name="locale" select="."/>
                        <li role="presentation">
                            <xsl:if test="$locale = $active-locale">
                                <xsl:attribute name="class">
                                    <xsl:text>disabled</xsl:text>
                                </xsl:attribute>
                            </xsl:if>
                            <a>
                                <xsl:attribute name="href">
                                    <xsl:value-of select="$current-uri"/>
                                    <xsl:text>?locale-attribute=</xsl:text>
                                    <xsl:value-of select="$locale"/>
                                </xsl:attribute>
                                <xsl:value-of
                                        select="/dri:document/dri:meta/dri:pageMeta/dri:metadata[@element='supportedLocale'][@qualifier=$locale]"/>
                            </a>
                        </li>
                    </xsl:for-each>
                </ul>
            </li>
        </xsl:if>
    </xsl:template>

    <xsl:template name="buildAuburnGDPR">
        <div class="content_row hidden-print" id="gdpr" style="display:none;">
            <div class="alert alert-info" role="contentinfo">
                <button aria-label="Close" class="close" data-dismiss="alert" id="cookieAcknowledge" type="button">
                    <span aria-hidden="true">x</span>
                </button>
                <p class="h4">Cookie Acknowledgement</p>
                <p class="h5">This website uses cookies to collect information to improve your browsing experience. Please review our 
                    <a href="http://www.auburn.edu/privacy" style="font-weight: bold; border-bottom:1px solid #a1a1a1;">Privacy Statement</a> for more information.
                </p>
            </div>
        </div>
    </xsl:template>

</xsl:stylesheet>
