$(function()
{
	var elements = {
		embargoUntilDate: $('input[name="embargo_until_date"]'),
		createEmbargoRadio: $('input[name="create_embargo_radio"]'),
		dateListItem: $('input[name="embargo_until_date"]').parent("div").parent("div"),
		datefieldDisplay: $('input[name="datefieldDisplay"]'),
		file: $(':file'),
		errorStack: $("#errorstack"),
		errorStackLink: $('#errorStackLink'),
	};

	elements.dateListItem.hide();
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
	}

	// Hide or show the date input field and embargoed group select field
	// based on the value of embargoSelectedVal.
	elements.createEmbargoRadio.each(function()
	{
		if($(this).is(":checked"))
		{
			var checkedVal = $(this).val();
			checkedVal = parseInt(checkedVal);
		}

		if(checkedVal == 2 || checkedVal == 3)
		{
			elements.dateListItem.show();
		}
		else if(checkedVal == 1)
		{
			elements.dateListItem.hide();
		}
	});
    
	InitializeActionElements();

	function InitializeActionElements()
	{
		elements.file.on("change", function()
		{
			if($(this).parent('div').hasClass('has-error'))
			{
				$(this).parent('div').removeClass('has-error');
				$(this).parent('div').find('.alert').remove();
			}
		});
		elements.createEmbargoRadio.on("click", function()
		{
			var name= $(this).attr("name");
			var selectedVal = 0;

			if($(this).is(":checked"))
			{
				var val = $(this).val();
				selectedVal = parseInt(val);
			}

			if(selectedVal == 2 || selectedVal == 3)
			{
				elements.dateListItem.show();
			}
			else if(selectedVal <= 1)
			{
				elements.dateListItem.hide();

				if(elements.embargoUntilDate.val() !== "")
				{
					elements.embargoUntilDate.val('');
				}
			}

			/**
			 * if an error status is attached to the embargo radio's parent
			 * elements then remove the error status and associated messages
			 *
			 * This action would only occur if the user submitted the form
			 * before selecting an embargo choice radio button.
			 */
			if($(this).parent('label').parent('div').parent('fieldset').hasClass('error') && $(this).parent('label').parent('div').parent('fieldset').parent('div').hasClass('has-error'))
			{
				$(this).parent('label').parent('div').parent('fieldset').removeClass('error');
				$(this).parent('label').parent('div').parent('fieldset').parent('div').removeClass('has-error');
				$(this).parent('label').parent('div').parent('fieldset').parent('div').find('.alert').remove();
			}
		});

		elements.embargoUntilDate.on("change", function()
		{
			if($(this).parent('div').hasClass('has-error'))
			{
				$(this).parent('div').removeClass('has-error');
				$(this).parent('div').find('.alert').remove();
			}
		});

		elements.errorStackLink.on("click", function(e)
		{
			e.preventDefault();
			elements.errorStack.toggleClass('hidden');
			e.stopPropagation();
		});
	}

    function ConvertMultiSelect()
    {
        if($(window).width() < 768)
        {
            $("select").each(function()
            {
                var multiple = $(this).attr("multiple");
                var id = $(this).attr("id");
                var $props = {closeOnSelect: false};

                if(multiple)
                {
                    if(id == "aspect_submission_StepTransformer_field_dc_type_genre")
                    {
                        $props = {
                            placeholder: "Select a contribution type",
                            closeOnSelect: false
                        };
                    }

                    $(this).select2($props);
                }
            });
        }
        else {
            $("select").each(function()
            {
                var multiple = $(this).attr("multiple");

                if(multiple)
                {
                    $(this).select2("destroy");
                }
            });
        }
    }

    ConvertMultiSelect();
    
    /* TOGGLE ARROW FOR MAIN NAVIGATION LINK IN MOBILE VIEW */
	$(".header-wrap .navbar-brand").click(
		function(){
			if ($(this).hasClass('active')){
				$(".header-wrap .navbar-brand span").css("background","");
				$(this).removeClass('active');
			} else {
				$(".header-wrap .navbar-brand span").css("background","url('https://www.auburn.edu/template/2013/assets/img/glyphicons-halflings-white.png') -285px -117px no-repeat");
				$(this).addClass('active');
			}
		}
	);
    
    /* TRIGGER FOR THE SEARCH COLLAPSIBLE AREA FOR MOBILE LAYOUT */
	$(".search-icon").click(
		function(){
			$(".search-form, .top-links").slideToggle();
		}
	);

	// bootstrap-filestyle.min.js - Styling the file input tag on the file upload submission screen.
	elements.file.filestyle();

    $(window).resize(function()
    {
        ConvertMultiSelect();
    });
});