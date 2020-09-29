/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.core;

public class AUETDConstants {

    public static final String EMBARGOED = "EMBARGOED";
    public static final String NOT_EMBARGOED = "NOT_EMBARGOED";
    public static final String EMBARGO_NOT_AUBURN_STR = "EMBARGO_NOT_AUBURN";
    public static final String EMBARGO_GLOBAL_STR = "EMBARGO_GLOBAL";
    public static final String AUETD_FILE_UPLOAD_ERROR_KEY = "FILE_UPLOAD_ERROR";
    public static final String AUETD_PREVIOUS_BITSTREAM_NAME_HASH_KEY_NAME = "previousBitstreamName";
    public static final String AUETD_NEW_BITSTREAM_NAME_HASH_KEY_NAME = "newBitstreamName";
    public static final String AUETD_PREVIOUS_EMBARGO_STATUS_HASH_KEY_NAME = "previousEmbargoStatus";
    public static final String AUETD_NEW_EMBARGO_STATUS_HASH_KEY_NAME = "newEmbargoStatus";
    public static final String AUETD_PREVIOUS_EMBARGO_LENGTH_HASH_KEY_NAME = "previousEmbargoLength";
    public static final String AUETD_NEW_EMBARGO_LENGTH_HASH_KEY_NAME = "newEmbargoLength";
    public static final String AUETD_PREVIOUS_EMBARGO_END_DATE_HASH_KEY_NAME = "previousEmbargoEndDate";
    public static final String AUETD_NEW_EMBARGO_END_DATE_HASH_KEY_NAME = "newEmbargoEndDate";
    public static final String AUETD_PREVIOUS_EMBARGO_RIGHTS_HASH_KEY_NAME = "previousEmbargoRights";
    public static final String AUETD_NEW_EMBARGO_RIGHTS_HASH_KEY_NAME = "newEmbargoRights";
    public static final String AUETD_CREATE_EMBARGO_QUESTION_FIELD_NAME = "create_embargo_radio";
    public static final String AUETD_CREATE_EMBARGO_QUESTION_FIELD_NAME_ERROR = AUETD_CREATE_EMBARGO_QUESTION_FIELD_NAME+"_ERROR";
    public static final String AUETD_EMBARGO_LENGTH_FIELD_NAME = "embargo_length";
    public static final String AUETD_EMBARGO_LENGTH_FIELD_NAME_REQUIRED_ERROR = AUETD_EMBARGO_LENGTH_FIELD_NAME+"_REQUIRED_ERROR";
    public static final String AUETD_EMBARGO_LENGTH_FIELD_NAME_OUT0FDATE_ERROR = AUETD_EMBARGO_LENGTH_FIELD_NAME+"_OUTOFDATE_ERROR";
    public static final String AUETD_SUBMIT_REMOVE_SELECTED = "submit_remove_selected";
    public static final String AUETD_SUBMIT_EDIT_PREFIX = "submit_edit_";
    public static final String AUETD_SUBMIT_REMOVE_PREFIX = "submit_remove_";
    public static final String AUETD_EMBARGO_LENGTH_FIELD_NAME_ERROR = AUETD_EMBARGO_LENGTH_FIELD_NAME + "_ERROR";
    public static final String AUETD_ACCESS_SAVE_BUTTON_ID = "submit_access";
    public static final String AUETD_ERROR_FLAG_LOG_MESSAGE = " Error Flag = ";
    
    public static final int AUETD_STATUS_UNACCEPTABLE_FORMAT = 11;
    public static final int AUETD_STATUS_ERROR = 35;
    public static final int AUETD_STATUS_ERROR_EMBARGO_CREATION_REQUIRED = 36;
    public static final int AUETD_STATUS_ERROR_EMBARGO_LENGTH_REQUIRED = 37;
}