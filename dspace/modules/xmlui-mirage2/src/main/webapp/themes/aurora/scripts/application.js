$(function ()
{
    var elements = {
        file: $(':file'),
        errorStack: $("#errorstack"),
        errorStackLink: $('#errorStackLink'),
    };

    InitializeActionElements();

    function InitializeActionElements()
    {
        elements.errorStackLink.on("click", function (e)
        {
            e.preventDefault();
            elements.errorStack.toggleClass('hidden');
            e.stopPropagation();
        });
    }

    function setSidebarSearchVisibility()
    {
        if ($(window).width() > 768)
        {
            $("#ds-search-option").show();
        } else {
            $("#ds-search-option").hide();
        }
    }

    function ConvertMultiSelect()
    {
        if ($(window).width() < 768)
        {
            $("select").each(function ()
            {
                var multiple = $(this).attr("multiple");
                var id = $(this).attr("id");
                var $props = {closeOnSelect: false};

                if (multiple)
                {
                    if (id == "aspect_submission_StepTransformer_field_dc_type_genre")
                    {
                        $props = {
                            placeholder: "Select a contribution type",
                            closeOnSelect: false
                        };
                    }

                    $(this).select2($props);
                }
            });
        } else {
            $("select").each(function (e)
            {
                var multiple = $(this).attr("multiple");

                if (multiple)
                {
                    $(this).select2("destroy");
                }
            });
        }
    }

    ConvertMultiSelect();

    setSidebarSearchVisibility();

    /* TOGGLE ARROW FOR MAIN NAVIGATION LINK IN MOBILE VIEW */
    $(".header-wrap .navbar-brand").click(
            function () {
                if ($(this).hasClass('active')) {
                    $(".header-wrap .navbar-brand span").css("background", "");
                    $(this).removeClass('active');
                } else {
                    $(".header-wrap .navbar-brand span").css("background", "url('https://www.auburn.edu/template/2013/assets/img/glyphicons-halflings-white.png') -285px -117px no-repeat");
                    $(this).addClass('active');
                }
            }
    );

    // bootstrap-filestyle.min.js - Styling the file input tag on the file upload submission screen.
    elements.file.filestyle();

    $(window).resize(function ()
    {
        setSidebarSearchVisibility();
        ConvertMultiSelect();
    });
});