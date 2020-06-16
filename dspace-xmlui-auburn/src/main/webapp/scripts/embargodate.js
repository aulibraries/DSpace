$(function() {
    var elements = {
        createEmbargoRadio: $('input[name="create_embargo_radio"]'),
        embargoLength: $('input[name="embargo_length"]'),
        embargoLengthItem: $('input[name="embargo_length"]:first').parent("label").parent("div").parent("fieldset").parent("div"),
        embargoLengthFieldDisplay: $('input[name="embargoLengthFieldDisplay"]'),
    };

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
            elements.embargoLengthItem.show();
        } else if (checkedVal == 1) {
            elements.embargoLengthItem.hide();
        }
    });

    initializeEmbargoDateActionElements();

    function initializeEmbargoDateActionElements() {
        elements.createEmbargoRadio.on("click", function () {
            var name = $(this).attr("name");
            var selectedVal = 0;

            if ($(this).is(":checked")) {
                var val = $(this).val();
                selectedVal = parseInt(val);
            }

            if (selectedVal == 2 || selectedVal == 3) {
                elements.embargoLengthItem.show();
            } else if (selectedVal <= 1) {
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
            if ($(this).parent('label').parent('div').parent('fieldset').parent('div').hasClass('has-error')) {
                $(this).parent('label').parent('div').parent('fieldset').parent('div').removeClass('has-error');
                $(this).parent('label').parent('div').parent('fieldset').parent('div').find('p.alert').hide();
            }
        });

        elements.embargoLength.on("click", function () {
            if ($(this).parent('label').parent('div').parent('fieldset').parent('div').hasClass('has-error')) {
                $(this).parent('label').parent('div').parent('fieldset').parent('div').removeClass('has-error');
                $(this).parent('label').parent('div').parent('fieldset').parent('div').find('p.alert').hide();
            }
        });
    }
});