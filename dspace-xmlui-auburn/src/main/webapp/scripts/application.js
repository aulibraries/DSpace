/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

$(function () {
    var elements = {
        file: $(':file'),
        errorStack: $("#errorstack"),
        errorStackLink: $('#errorStackLink'),
        dropdown: $("#dropdown"),
        sidebarToggle: $(".sidebarToggle"),
    };

    if (typeof GATC === 'function') {
        GATC();
    }
    $('#cookieAcknowledge').on('click', function () {
        var d = new Date();
        // set expiration date for one year
        d.setTime(d.getTime() + (365 * 24 * 60 * 60 * 1000));
        var expires = "expires=" + d.toUTCString();
        document.cookie = "auburnGDPR=acknowledged; " + expires + "; domain=.auburn.edu; path=/";
        $("#gdpr").fadeOut();
    });
    $('#cookieExpire').on('click', function () {
        document.cookie = "auburnGDPR=deny; expires=Sat, 16 Feb 1980 12:05:00 UTC; domain=.auburn.edu; path=/";
        $("#curStatus").text("Cookies has expired.");
        $("#gdpr").fadeOut();
    });
    $('#pageRefresh').on('click', function () {
        window.location.reload(false);
    });

    var cookieStatus = getCookie("auburnGDPR");
    if (cookieStatus === "acknowledged") {
        $("#gdpr").hide();
        $("#curStatus").text("Cookies were previously acknowledged.");
    } else {
        $("#gdpr").fadeIn();
    }

    initializeActionElements();

    function initializeActionElements() {
        $('#sidebarToggle').on('click', function () {
            $('.row-offcanvas').toggleClass('active');
            $(this).toggleClass('active');

            if ($(this).hasClass('active')) {
                $(this).html("< Close Nav");
            } else {
                $(this).html("View Nav >");
            }
        });

        elements.file.on("change", function () {
            if ($(this).parent('div').hasClass('has-error')) {
                $(this).parent('div').removeClass('has-error');
                $(this).parent('div').find('.alert').remove();
            }
        });
        
        elements.errorStackLink.on("click", function (e) {
            e.preventDefault();
            elements.errorStack.toggleClass('hidden');
            e.stopPropagation();
        });
    }

    function getCookie(cname) {
        var name = cname + "=";
        var ca = document.cookie.split(';');
        for (var i = 0; i < ca.length; i++) {
            var c = ca[i];
            while (c.charAt(0) == ' ') {
                c = c.substring(1);
            }
            if (c.indexOf(name) == 0) {
                return c.substring(name.length, c.length);
            }
        }
        return "";
    }

    function convertMultiSelect() {
        if ($(window).width() < 768) {
            $("select").each(function () {
                var multiple = $(this).attr("multiple");
                var id = $(this).attr("id");
                var $props = {
                    closeOnSelect: false,
                    allowClear: true
                };

                if (multiple) {
                    if (id == "aspect_submission_StepTransformer_field_dc_type_genre") {
                        $props = {
                            placeholder: "Select a contribution type",
                        };
                    }

                    $(this).select2($props);
                }
            });
        } else {
            $("select").each(function () {
                if ($(this).select2("destroy")) {
                    $(this).select2("destroy");
                }
            });
        }
    }

    function toggleSidebarNavButton() {
        if ($(window).width() < 768) {
            elements.sidebarToggle.show();
        } else {
            elements.sidebarToggle.hide();
        }
    }

    function changeSubmissionBttnSize() {
        $("a").each(function () {
            if ($(this).hasClass("submissionBttn")) {
                if ($(window).width() <= 480) {
                    $(this).addClass("btn-sm");
                } else if ($(window).width() > 480 && $(this).hasClass("btn-sm")) {
                    $(this).removeClass("btn-sm");
                }
            }
        });
    }

    convertMultiSelect();
    changeSubmissionBttnSize();
    toggleSidebarNavButton();

    $(window).resize(function () {
        convertMultiSelect();
        changeSubmissionBttnSize();
        toggleSidebarNavButton();
    });
});
