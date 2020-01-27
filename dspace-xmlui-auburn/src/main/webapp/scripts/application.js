/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

$(function () {
    var elements = {
        //embargoUntilDate: $('input[name="embargo_until_date"]'),
        createEmbargoRadio: $('input[name="create_embargo_radio"]'),
        //dateListItem: $('input[name="embargo_until_date"]').parent("div").parent("div"),
        //datefieldDisplay: $('input[name="datefieldDisplay"]'),
        embargoLength: $('input[name="embargo_length"]'),
        embargoLengthItem: $('input[name="embargo_length"]:first').parent("label").parent("div").parent("fieldset").parent("div"),
        embargoLengthFieldDisplay: $('input[name="embargoLengthFieldDisplay"]'),
        file: $(':file'),
        errorStack: $("#errorstack"),
        errorStackLink: $('#errorStackLink'),
        dropdown: $("#dropdown"),
    };

    /*elements.dateListItem.hide();
    elements.embargoUntilDate.datepicker("destroy");
    elements.embargoUntilDate.datepicker(
    {
        changeMonth: true,
        changeYear: true,
        dateFormat: 'yy-mm-dd',
        yearRange: '-0:+5'
    });

    if(elements.datefieldDisplay.val() != "")
    {
        if(parseInt(elements.datefieldDisplay.val()) === 1)
        {
            elements.dateListItem.show();
        }
    }*/

    elements.embargoLengthItem.hide();

    if (elements.embargoLengthFieldDisplay.val() != "") {
        if (parseInt(elements.embargoLengthFieldDisplay.val()) === 1) {
            elements.embargoLengthItem.show();
        }
    }

    // Hide or show the date input field and embargoed group select field
    // based on the value of embargoSelectedVal.
    elements.createEmbargoRadio.each(function () {
        var checkedVal = 0;
        if ($(this).is(":checked")) {
            checkedVal = $(this).val();
            checkedVal = parseInt(checkedVal);
        }

        if (checkedVal == 2 || checkedVal == 3) {
            //elements.dateListItem.show();
            elements.embargoLengthItem.show();
        } else if (checkedVal == 1) {
            //elements.dateListItem.hide();
            elements.embargoLengthItem.hide();
        }
    });

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

    InitializeActionElements();

    function InitializeActionElements() {
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
        elements.createEmbargoRadio.on("click", function () {
            var name = $(this).attr("name");
            var selectedVal = 0;

            if ($(this).is(":checked")) {
                var val = $(this).val();
                selectedVal = parseInt(val);
            }

            if (selectedVal == 2 || selectedVal == 3) {
                //elements.dateListItem.show();
                elements.embargoLengthItem.show();
            } else if (selectedVal <= 1) {
                /*elements.dateListItem.hide();

                if(elements.embargoUntilDate.val() !== "")
                {
                    elements.embargoUntilDate.val('');
                }*/

                elements.embargoLengthItem.hide();

                elements.embargoLength.each(function () {
                    if ($(this).is(":checked")) {
                        $(this).prop("checked", false);
                    }
                });
            }

            /**
             * if an error status is attached to the embargo radio's parent
             * elements then remove the error status and associated messages
             *
             * This action would only occur if the user submitted the form
             * before selecting an embargo choice radio button.
             */
            if ($(this).parent('label').parent('div').parent('fieldset').hasClass('error') && $(this).parent('label').parent('div').parent('fieldset').parent('div').hasClass('has-error')) {
                $(this).parent('label').parent('div').parent('fieldset').removeClass('error');
                $(this).parent('label').parent('div').parent('fieldset').parent('div').removeClass('has-error');
                $(this).parent('label').parent('div').parent('fieldset').parent('div').find('.alert').remove();
            }
        });

        /*elements.embargoUntilDate.on("change", function()
        {
            if($(this).parent('div').hasClass('has-error'))
            {
                $(this).parent('div').removeClass('has-error');
                $(this).parent('div').find('.alert').remove();
            }
        });*/

        elements.embargoLength.on("click", function () {
            if ($(this).parent('label').parent('div').parent('fieldset').hasClass('error') && $(this).parent('label').parent('div').parent('fieldset').parent('div').hasClass('has-error')) {
                $(this).parent('label').parent('div').parent('fieldset').removeClass('error');
                $(this).parent('label').parent('div').parent('fieldset').parent('div').removeClass('has-error');
                $(this).parent('label').parent('div').parent('fieldset').parent('div').find('.alert').remove();
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

    function ConvertMultiSelect() {
        if ($(window).width() < 768) {
            $("select").each(function () {
                var multiple = $(this).attr("multiple");
                var id = $(this).attr("id");
                var $props = {
                    closeOnSelect: false
                };

                if (multiple) {
                    if (id == "aspect_submission_StepTransformer_field_dc_type_genre") {
                        $props = {
                            placeholder: "Select a contribution type",
                            closeOnSelect: false
                        };
                    }

                    $(this).select2($props);
                }
            });
        } else {
            $("select").each(function () {
                var multiple = $(this).attr("multiple");

                if (multiple) {
                    $(this).select2("destroy");
                }
            });
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

    ConvertMultiSelect();
    changeSubmissionBttnSize();

    $(window).resize(function () {
        ConvertMultiSelect();
        changeSubmissionBttnSize();
    });
});
