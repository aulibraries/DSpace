/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.xmlui.aspect.administrative;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.apache.cocoon.environment.Request;
import org.apache.cocoon.servlet.multipart.Part;
import org.apache.commons.lang.time.DateUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.dspace.app.util.Util;
import org.dspace.app.xmlui.utils.UIException;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.core.Email;
import org.dspace.core.LogManager;
import org.dspace.content.*;
import org.dspace.content.Collection;
import org.dspace.content.authority.Choices;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.*;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.services.ConfigurationService;
import org.dspace.core.AUETDConstants;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.I18nUtil;
import org.dspace.curate.Curator;
import org.dspace.embargo.factory.EmbargoServiceFactory;
import org.dspace.embargo.service.EmbargoService;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.eperson.service.GroupService;
import org.dspace.handle.factory.HandleServiceFactory;
import org.dspace.handle.service.HandleService;

import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;

/**
 * Utility methods to processes actions on Groups. These methods are used
 * exclusively from the administrative flow scripts.
 * 
 * @author Jay Paz
 * @author Scott Phillips
 */
public class FlowItemUtils {

    /** Language Strings */
    private static final Message T_metadata_updated = new Message("default",
            "The Item's metadata was successfully updated.");
    private static final Message T_metadata_added = new Message("default", "New metadata was added.");
    private static final Message T_item_withdrawn = new Message("default", "The item has been withdrawn.");
    private static final Message T_item_public = new Message("default", "The item is now public.");
    private static final Message T_item_private = new Message("default", "The item is now private.");
    private static final Message T_item_reinstated = new Message("default", "The item has been reinstated.");
    private static final Message T_item_moved = new Message("default", "The item has been moved.");
    private static final Message T_item_move_destination_not_found = new Message("default",
            "The selected destination collection could not be found.");
    private static final Message T_bitstream_added = new Message("default",
            "The new bitstream was successfully uploaded.");
    private static final Message T_bitstream_failed = new Message("default", "Error while uploading file.");
    private static final Message T_bitstream_updated = new Message("default", "The bitstream has been updated.");
    private static final Message T_bitstream_delete = new Message("default",
            "The selected bitstreams have been deleted.");
    private static final Message T_bitstream_order = new Message("default",
            "The bitstream order has been successfully altered.");

    protected static final AuthorizeService authorizeService = AuthorizeServiceFactory.getInstance()
            .getAuthorizeService();

    protected static final ItemService itemService = ContentServiceFactory.getInstance().getItemService();
    protected static final BundleService bundleService = ContentServiceFactory.getInstance().getBundleService();
    protected static final BitstreamService bitstreamService = ContentServiceFactory.getInstance()
            .getBitstreamService();
    protected static final CollectionService collectionService = ContentServiceFactory.getInstance()
            .getCollectionService();
    protected static final MetadataFieldService metadataFieldService = ContentServiceFactory.getInstance()
            .getMetadataFieldService();

    protected static final BitstreamFormatService bitstreamFormatService = ContentServiceFactory.getInstance()
            .getBitstreamFormatService();

    protected static final HandleService handleService = HandleServiceFactory.getInstance().getHandleService();

    protected static final EPersonService epersonService = EPersonServiceFactory.getInstance().getEPersonService();
    protected static final GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
    protected static final ConfigurationService configurationService = DSpaceServicesFactory.getInstance()
            .getConfigurationService();
    protected static final EmbargoService embargoService = EmbargoServiceFactory.getInstance().getEmbargoService();

    private static final Logger log = Logger.getLogger(FlowItemUtils.class);

    private static final String AUETD_PREVIOUS_BITSTREAM_NAME_HASH_KEY_NAME = "previousBitstreamName";
    private static final String AUETD_NEW_BITSTREAM_NAME_HASH_KEY_NAME = "newBitstreamName";
    private static final String AUETD_PREVIOUS_EMBARGO_STATUS_HASH_KEY_NAME = "previousEmbargoStatus";
    private static final String AUETD_NEW_EMBARGO_STATUS_HASH_KEY_NAME = "newEmbargoStatus";
    private static final String AUETD_PREVIOUS_EMBARGO_LENGTH_HASH_KEY_NAME = "previousEmbargoLength";
    private static final String AUETD_NEW_EMBARGO_LENGTH_HASH_KEY_NAME = "newEmbargoLength";
    private static final String AUETD_PREVIOUS_EMBARGO_END_DATE_HASH_KEY_NAME = "previousEmbargoEndDate";
    private static final String AUETD_NEW_EMBARGO_END_DATE_HASH_KEY_NAME = "newEmbargoEndDate";
    private static final String AUETD_PREVIOUS_EMBARGO_RIGHTS_HASH_KEY_NAME = "previousEmbargoRights";
    private static final String AUETD_NEW_EMBARGO_RIGHTS_HASH_KEY_NAME = "newEmbargoRights";
    public static final String AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME = "create_embargo_radio";
    public static final String AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME_ERROR = AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME+"_ERROR";
    public static final String AUETD_EMBARGO_LENGTH_FIELD_NAME = "embargo_length";
    public static final String AUETD_EMBARGO_LENGTH_FIELD_NAME_REQUIRED_ERROR = AUETD_EMBARGO_LENGTH_FIELD_NAME+"_REQUIRED_ERROR";
    public static final String AUETD_EMBARGO_LENGTH_FIELD_NAME_OUT0FDATE_ERROR = AUETD_EMBARGO_LENGTH_FIELD_NAME+"_OUTOFDATE_ERROR";
    public static final int AUETD_STATUS_ERROR_EMBARGO_CREATION_REQUIRED = 36;
    public static final int AUETD_STATUS_ERROR_EMBARGO_LENGTH_REQUIRED = 37;

    private static HashMap<String, String> embargoDataMap = new HashMap<String, String>();

    /**
     * Resolve the given identifier to an item. The identifier may be either an
     * internal ID or a handle. If an item is found then the result the internal ID
     * of the item will be placed in the result "itemID" parameter.
     * 
     * If the identifier was unable to be resolved to an item then the "identifier"
     * field is placed in error.
     * 
     * @param context    The current DSpace context.
     * @param identifier An Internal ID or a handle
     * @return A flow result
     * @throws java.sql.SQLException passed through.
     */
    public static FlowResult resolveItemIdentifier(Context context, String identifier) throws SQLException
    {
        FlowResult result = new FlowResult();
        result.setContinue(false);

        // Check whether it's a handle or internal id (by check ing if it has a slash
        // inthe string)
        if (identifier.contains("/")) {
            DSpaceObject dso = handleService.resolveToObject(context, identifier);

            if (dso != null && dso.getType() == Constants.ITEM) {
                result.setParameter("itemID", dso.getID());
                result.setParameter("type", Constants.ITEM);
                result.setContinue(true);
                return result;
            }
        } else {
            Item item = null;

            try {
                item = itemService.find(context, UUID.fromString(identifier));
            } catch (NumberFormatException e) {
                // ignoring the exception
            }

            if (item != null) {
                result.setParameter("itemID", item.getID());
                result.setParameter("type", Constants.ITEM);
                result.setContinue(true);
                return result;
            }
        }

        result.addError("identifier");
        return result;
    }

    /**
     * Process the request parameters to update the item's metadata and remove any
     * selected bitstreams.
     * 
     * Each metadata entry will have three fields "name_X", "value_X", and
     * "language_X" where X is an integer that relates all three of the fields
     * together. The name parameter stores the metadata name that is used by the
     * entry (i.e schema_element_qualifier). The value and language parameters are
     * user inputed fields. If the optional parameter "remove_X" is given then the
     * metadata value is removed.
     * 
     * To support AJAX operations on this page an additional parameter is
     * considered, the "scope". The scope is the set of metadata entries that are
     * being updated during this request. It the metadata name,
     * schema_element_qualifier, only fields that have this name are considered! If
     * all fields are to be considered then scope should be set to "*".
     * 
     * When creating an AJAX query include all the name_X, value_X, language_X, and
     * remove_X for the fields in the set, and then set the scope parameter to be
     * the metadata field.
     * 
     * @param context The current DSpace context
     * @param itemID  internal item id
     * @param request the Cocoon request
     * @return A flow result
     * @throws java.sql.SQLException                   passed through.
     * @throws org.dspace.authorize.AuthorizeException passed through.
     * @throws org.dspace.app.xmlui.utils.UIException  passed through.
     * @throws java.io.IOException                     passed through.
     */
    public static FlowResult processEditItem(Context context, UUID itemID, Request request)
        throws SQLException, AuthorizeException, UIException, IOException
    {
        FlowResult result = new FlowResult();
        result.setContinue(false);

        Item item = itemService.find(context, itemID);

        // STEP 1:
        // Clear all metadata within the scope
        // Only metadata values within this scope will be considered. This
        // is so ajax request can operate on only a subset of the values.
        String scope = request.getParameter("scope");
        if ("*".equals(scope)) {
            itemService.clearMetadata(context, item, Item.ANY, Item.ANY, Item.ANY, Item.ANY);
        } else {
            String[] parts = parseName(scope);
            itemService.clearMetadata(context, item, parts[0], parts[1], parts[2], Item.ANY);
        }

        // STEP 2:
        // First determine all the metadata fields that are within
        // the scope parameter
        ArrayList<Integer> indexes = new ArrayList<Integer>();
        Enumeration parameters = request.getParameterNames();
        while (parameters.hasMoreElements()) {

            // Only consider the name_ fields
            String parameterName = (String) parameters.nextElement();
            if (parameterName.startsWith("name_")) {
                // Check if the name is within the scope
                String parameterValue = request.getParameter(parameterName);
                if ("*".equals(scope) || scope.equals(parameterValue)) {
                    // Extract the index from the name.
                    String indexString = parameterName.substring("name_".length());
                    Integer index = Integer.valueOf(indexString);
                    indexes.add(index);
                }
            }
        }

        // STEP 3:
        // Iterate over all the indexes within the scope and add them back in.
        for (Integer index = 1; index <= indexes.size(); ++index) {
            String name = request.getParameter("name_" + index);
            String value = request.getParameter("value_" + index);
            String authority = request.getParameter("value_" + index + "_authority");
            String confidence = request.getParameter("value_" + index + "_confidence");
            String lang = request.getParameter("language_" + index);
            String remove = request.getParameter("remove_" + index);

            // the user selected the remove checkbox.
            if (remove != null) {
                continue;
            }

            // get the field's name broken up
            String[] parts = parseName(name);

            // probe for a confidence value
            int iconf = Choices.CF_UNSET;
            if (confidence != null && confidence.length() > 0) {
                iconf = Choices.getConfidenceValue(confidence);
            }
            // upgrade to a minimum of NOVALUE if there IS an authority key
            if (authority != null && authority.length() > 0 && iconf == Choices.CF_UNSET) {
                iconf = Choices.CF_NOVALUE;
            }
            itemService.addMetadata(context, item, parts[0], parts[1], parts[2], lang, value, authority, iconf);
        }

        itemService.update(context, item);

        result.setContinue(true);

        result.setOutcome(true);
        result.setMessage(T_metadata_updated);

        return result;
    }

    /**
     * Process the request parameters to add a new metadata entry for the item.
     * 
     * @param context The current DSpace context
     * @param itemID  internal item id
     * @param request the Cocoon request
     * @return A flow result
     * @throws java.sql.SQLException                   passed through.
     * @throws org.dspace.authorize.AuthorizeException passed through.
     * @throws org.dspace.app.xmlui.utils.UIException  passed through.
     * @throws java.io.IOException                     passed through.
     */
    public static FlowResult processAddMetadata(Context context, UUID itemID, Request request)
        throws SQLException, AuthorizeException, UIException, IOException
    {
        FlowResult result = new FlowResult();
        result.setContinue(false);

        Item item = itemService.find(context, itemID);

        String fieldID = request.getParameter("field");
        String value = request.getParameter("value");
        String language = request.getParameter("language");

        MetadataField field = metadataFieldService.find(context, Integer.valueOf(fieldID));

        itemService.addMetadata(context, item, field.getMetadataSchema().getName(), field.getElement(),
                field.getQualifier(), language, value);

        itemService.update(context, item);

        result.setContinue(true);

        result.setOutcome(true);
        result.setMessage(T_metadata_added);

        return result;
    }

    /**
     * Withdraw the specified item, this method assumes that the action has been
     * confirmed.
     * 
     * @param context The DSpace context
     * @param itemID  The id of the to-be-withdrawn item.
     * @return A result object
     * @throws java.sql.SQLException                   passed through.
     * @throws org.dspace.authorize.AuthorizeException passed through.
     * @throws java.io.IOException                     passed through.
     */
    public static FlowResult processWithdrawItem(Context context, UUID itemID)
        throws SQLException, AuthorizeException, IOException
    {
        FlowResult result = new FlowResult();
        result.setContinue(false);

        Item item = itemService.find(context, itemID);
        itemService.withdraw(context, item);

        result.setContinue(true);
        result.setOutcome(true);
        result.setMessage(T_item_withdrawn);

        return result;
    }

    /**
     * Reinstate the specified item, this method assumes that the action has been
     * confirmed.
     * 
     * @param context The DSpace context
     * @param itemID  The id of the to-be-reinstated item.
     * @return A result object
     * @throws java.sql.SQLException                   passed through.
     * @throws org.dspace.authorize.AuthorizeException passed through.
     * @throws java.io.IOException                     passed through.
     */
    public static FlowResult processReinstateItem(Context context, UUID itemID)
        throws SQLException, AuthorizeException, IOException
    {
        FlowResult result = new FlowResult();
        result.setContinue(false);

        Item item = itemService.find(context, itemID);
        itemService.reinstate(context, item);

        result.setContinue(true);
        result.setOutcome(true);
        result.setMessage(T_item_reinstated);

        return result;
    }

    /**
     * Make the specified item Private, this method assumes that the action has been
     * confirmed.
     *
     * @param context The DSpace context
     * @param itemID  The id of the to-be-withdrawn item.
     * @return A result object
     * @throws java.sql.SQLException                   passed through.
     * @throws org.dspace.authorize.AuthorizeException passed through.
     * @throws java.io.IOException                     passed through.
     */
    public static FlowResult processPrivateItem(Context context, UUID itemID)
        throws SQLException, AuthorizeException, IOException
    {
        FlowResult result = new FlowResult();
        result.setContinue(false);

        Item item = itemService.find(context, itemID);
        item.setDiscoverable(false);
        itemService.update(context, item);

        result.setContinue(true);
        result.setOutcome(true);
        result.setMessage(T_item_private);

        return result;
    }

    /**
     * Make the specified item Private, this method assumes that the action has been
     * confirmed.
     *
     * @param context The DSpace context
     * @param itemID  The id of the to-be-withdrawn item.
     * @return A result object
     * @throws java.sql.SQLException                   passed through.
     * @throws org.dspace.authorize.AuthorizeException passed through.
     * @throws java.io.IOException                     passed through.
     */
    public static FlowResult processPublicItem(Context context, UUID itemID)
        throws SQLException, AuthorizeException, IOException
    {
        FlowResult result = new FlowResult();
        result.setContinue(false);

        Item item = itemService.find(context, itemID);
        item.setDiscoverable(true);
        itemService.update(context, item);

        result.setContinue(true);
        result.setOutcome(true);
        result.setMessage(T_item_public);

        return result;
    }

    /**
     * Move the specified item to another collection.
     *
     * @param context      The DSpace context
     * @param itemID       The id of the to-be-moved item.
     * @param collectionID The id of the destination collection.
     * @param inherit      Whether to inherit the policies of the destination
     *                     collection
     * @return A result object
     * @throws java.sql.SQLException                   passed through.
     * @throws org.dspace.authorize.AuthorizeException passed through.
     * @throws java.io.IOException                     passed through.
     */
    public static FlowResult processMoveItem(Context context, UUID itemID, UUID collectionID, boolean inherit)
        throws SQLException, AuthorizeException, IOException
    {
        FlowResult result = new FlowResult();
        result.setContinue(false);

        Item item = itemService.find(context, itemID);

        if (authorizeService.isAdmin(context, item)) {
            // Add an action giving this user *explicit* admin permissions on the item
            // itself.
            // This ensures that the user will be able to call itemService.update(context,
            // item) even if he/she
            // moves it to a Collection that he/she doesn't administer.
            if (itemService.canEdit(context, item)) {
                authorizeService.authorizeAction(context, item, Constants.WRITE);
            }

            Collection destination = collectionService.find(context, collectionID);
            if (destination == null) {
                result.setOutcome(false);
                result.setContinue(false);
                result.setMessage(T_item_move_destination_not_found);
                return result;
            }

            Collection owningCollection = item.getOwningCollection();
            if (destination.equals(owningCollection)) {
                // nothing to do
                result.setOutcome(false);
                result.setContinue(false);
                return result;
            }

            // note: an item.move() method exists, but does not handle several cases:
            // - no preexisting owning collection (first arg is null)
            // - item already in collection, but not an owning collection
            // (works, but puts item in collection twice)

            // Don't re-add the item to a collection it's already in.
            boolean alreadyInCollection = false;
            for (Collection collection : item.getCollections()) {
                if (collection.equals(destination)) {
                    alreadyInCollection = true;
                    break;
                }
            }

            // Remove item from its owning collection and add to the destination
            if (!alreadyInCollection) {
                collectionService.addItem(context, destination, item);
            }

            if (owningCollection != null) {
                collectionService.removeItem(context, owningCollection, item);
            }

            item.setOwningCollection(destination);

            // Inherit policies of destination collection if required
            if (inherit) {
                itemService.inheritCollectionDefaultPolicies(context, item, destination);
            }

            itemService.update(context, item);

            result.setOutcome(true);
            result.setContinue(true);
            result.setMessage(T_item_moved);
        }

        return result;
    }

    /**
     * Permanently delete the specified item, this method assumes that the action
     * has been confirmed.
     * 
     * @param context The DSpace context
     * @param itemID  The id of the to-be-deleted item.
     * @return A result object
     * @throws java.sql.SQLException                   passed through.
     * @throws org.dspace.authorize.AuthorizeException passed through.
     * @throws java.io.IOException                     passed through.
     */
    public static FlowResult processDeleteItem(Context context, UUID itemID)
        throws SQLException, AuthorizeException, IOException
    {
        FlowResult result = new FlowResult();
        result.setContinue(false);

        Item item = itemService.find(context, itemID);
        itemService.delete(context, item);

        result.setContinue(true);

        return result;
    }

    /**
     * Add a new bitstream to the item. The bundle, bitstream (aka file), and
     * description will be used to create a new bitstream. If the format needs to be
     * adjusted then they will need to access the edit bitstream form after it has
     * been uploaded.
     * 
     * @param context The DSpace content
     * @param itemID  The item to add a new bitstream too
     * @param request The request.
     * @return A flow result
     * @throws java.sql.SQLException                   passed through.
     * @throws org.dspace.authorize.AuthorizeException passed through.
     * @throws java.io.IOException                     passed through.
     */
    public static FlowResult processAddBitstream(Context context, UUID itemID, Request request)
        throws SQLException, AuthorizeException, IOException
    {
        FlowResult result = new FlowResult();
        result.setContinue(false);

        // Upload a new file
        Item item = itemService.find(context, itemID);

        Object object = request.get("file");
        Part filePart = null;
        if (object instanceof Part) {
            filePart = (Part) object;
        }

        if (filePart != null && filePart.getSize() > 0) {
            InputStream is = filePart.getInputStream();

            //String bundleName = request.getParameter("bundle");
            String bundleName = Constants.CONTENT_BUNDLE_NAME;

            Bitstream bitstream;
            List<Bundle> bundles = itemService.getBundles(item, bundleName);
            if (bundles.size() < 1) {
                // set bundle's name to ORIGINAL
                bitstream = itemService.createSingleBitstream(context, is, item, bundleName);

                // set the permission as defined in the owning collection
                Collection owningCollection = item.getOwningCollection();
                if (owningCollection != null) {
                    Bundle bnd = bitstream.getBundles().get(0);
                    bundleService.inheritCollectionDefaultPolicies(context, bnd, owningCollection);
                }
            } else {
                // we have a bundle already, just add bitstream
                bitstream = bitstreamService.create(context, bundles.get(0), is);
            }

            // Strip all but the last filename. It would be nice
            // to know which OS the file came from.
            String name = filePart.getUploadName();

            while (name.indexOf('/') > -1) {
                name = name.substring(name.indexOf('/') + 1);
            }

            while (name.indexOf('\\') > -1) {
                name = name.substring(name.indexOf('\\') + 1);
            }

            bitstream.setName(context, name);
            bitstream.setSource(context, filePart.getUploadName());
            //bitstream.setDescription(context, request.getParameter("description"));

            // Identify the format
            BitstreamFormat format = bitstreamFormatService.guessFormat(context, bitstream);
            bitstreamService.setFormat(context, bitstream, format);

            // Update to DB
            bitstreamService.update(context, bitstream);
            itemService.update(context, item);

            //processAccessFields(context, request, item.getOwningCollection(), bitstream);
            processAUETDEmbargoAccessFields(context, request, item, bitstream);

            result.setContinue(true);
            result.setOutcome(true);
            result.setMessage(T_bitstream_added);
        } else {
            result.setContinue(false);
            result.setOutcome(false);
            result.setMessage(T_bitstream_failed);
        }
        return result;
    }

    private static void processAccessFields(Context context, HttpServletRequest request, Collection collection,
        Bitstream b)
        throws SQLException, AuthorizeException
    {

        // if it is a simple form we should create the policy for Anonymous
        // if Anonymous does not have right on this collection, create policies for any
        // other groups with
        // DEFAULT_ITEM_READ specified.
        Date startDate = null;
        try {
            startDate = DateUtils.parseDate(request.getParameter("embargo_until_date"),
                    new String[] { "yyyy-MM-dd", "yyyy-MM", "yyyy" });
        } catch (Exception e) {
            // Ignore, start date is already null
        }
        String reason = request.getParameter("reason");
        authorizeService.generateAutomaticPolicies(context, startDate, reason, b, collection);
    }

    /**
     * Update a bitstream's metadata.
     * 
     * @param context       The DSpace content
     * @param itemID        The item to which the bitstream belongs
     * @param bitstreamID   The bitstream being updated.
     * @param bitstreamName the bitstream's name.
     * @param primary       "yes" or "no": is bitstream primary?
     * @param description   The new description of the bitstream
     * @param formatID      The new format ID of the bitstream
     * @param userFormat    Any user supplied formats.
     * @param request       user request.
     * @return A flow result object.
     * @throws java.sql.SQLException                   passed through.
     * @throws org.dspace.authorize.AuthorizeException passed through.
     */
    public static FlowResult processEditBitstream(Context context, UUID itemID, UUID bitstreamID, String bitstreamName,
        String primary, String description, int formatID, String userFormat, Request request)
        throws SQLException, AuthorizeException, IOException
    {
        FlowResult result = new FlowResult();
        result.setContinue(false);

        /**
         * Custom Code Section
         */
        int embargoCreationAnswer = 0;
        Item item = null;

        Bitstream bitstream = bitstreamService.find(context, bitstreamID);
        BitstreamFormat currentFormat = bitstream.getFormat(context);

        if (StringUtils.isNotBlank(bitstreamName)) {
            bitstream.setName(context, bitstreamName);
        }

        if (StringUtils.isNotBlank(itemID.toString())) {
            item = itemService.find(context, itemID);
        } else if (StringUtils.isNotBlank(bitstreamID.toString())) {
            DSpaceObject parent = bitstreamService.getParentObject(context, bitstream);

            if (parent != null) {
                item = itemService.find(context, parent.getID());
            }
        }

        setPreviousEmbargoData(item);

        if (StringUtils.isNotBlank(bitstreamName) && !bitstreamName.equals(bitstream.getName())) {
            if (embargoDataMap.containsKey(AUETD_PREVIOUS_BITSTREAM_NAME_HASH_KEY_NAME)) {
                embargoDataMap.remove(AUETD_PREVIOUS_BITSTREAM_NAME_HASH_KEY_NAME);
            }

            if (embargoDataMap.containsKey(AUETD_NEW_BITSTREAM_NAME_HASH_KEY_NAME)) {
                embargoDataMap.remove(AUETD_NEW_BITSTREAM_NAME_HASH_KEY_NAME);
            }

            embargoDataMap.put(AUETD_PREVIOUS_BITSTREAM_NAME_HASH_KEY_NAME, bitstream.getName());
            embargoDataMap.put(AUETD_NEW_BITSTREAM_NAME_HASH_KEY_NAME, bitstreamName);
        }

        // Step 2:
        // Check if the primary bitstream status has changed
        List<Bundle> bundles = bitstream.getBundles();
        if (bundles != null && bundles.size() > 0) {
            Bundle bundle = bundles.get(0);
            if (bundle.getPrimaryBitstream() != null
                    && bundle.getPrimaryBitstream().toString().equals(String.valueOf(bitstreamID))) {
                // currently the bitstream is primary
                if ("no".equals(primary)) {
                    // However the user has removed this bitstream as a primary bitstream.
                    bundle.setPrimaryBitstreamID(null);
                    bundleService.update(context, bundle);
                }
            } else {
                // currently the bitstream is non-primary
                if ("yes".equals(primary)) {
                    // However the user has set this bitstream as primary.
                    bundle.setPrimaryBitstreamID(bitstream);
                    bundleService.update(context, bundle);
                }
            }
        }

        //Step 2:
		// Update the bitstream's format
		if (formatID > 0) {
			if (currentFormat == null || currentFormat.getID() != formatID) {
				BitstreamFormat newFormat = bitstreamFormatService.find(context, formatID);
				if (newFormat != null) {
					bitstreamService.setFormat(context, bitstream, newFormat);
				}
			}
		} else {
			if (StringUtils.isNotBlank(userFormat) && userFormat.length() > 0) {
				bitstreamService.setUserFormatDescription(context, bitstream, userFormat);
			}
		}

        if (StringUtils.isNotBlank(request.getParameter(AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME))) {
            embargoCreationAnswer = Integer.parseInt(request.getParameter(AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME));
        } else {
            result.setOutcome(false);
            result.addError(AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME_ERROR);
            return result;
        }

        if (embargoCreationAnswer == 1 || embargoCreationAnswer == 2 || embargoCreationAnswer == 3) {
            if (embargoCreationAnswer == 2 || embargoCreationAnswer == 3) {
                if (StringUtils.isBlank(request.getParameter(AUETD_EMBARGO_LENGTH_FIELD_NAME))) {
                    result.setOutcome(false);
                    result.addError(AUETD_EMBARGO_LENGTH_FIELD_NAME_REQUIRED_ERROR);
                    return result;
                }

                /* if (embargoPeriodHasPassed(item)) {
                    result.setOutcome(false);
                    result.addError(AUETD_EMBARGO_LENGTH_FIELD_NAME_OUT0FDATE_ERROR);
                    return result;
                } */
            }
            processAUETDEmbargoAccessFields(context, request, item, bitstream);
        }

        // Step 3:
        // Save our changes
        bitstreamService.update(context, bitstream);

        createEmbargoEditProvenanceMessage(context, item);
        notifyEmabargoAdminGroup(context, item);

        result.setContinue(true);
        result.setOutcome(true);
        result.setMessage(T_bitstream_updated);

        return result;
    }

    /**
     * Delete the given bitstreams from the bundle and item. If there are no more
     * bitstreams left in a bundle then also remove it.
     * 
     * @param context      Current dspace content
     * @param itemID       The item id from which to remove bitstreams
     * @param bitstreamIDs A bundleID slash bitstreamID pair of bitstreams to be
     *                     removed.
     * @return A flow result
     * @throws java.sql.SQLException                   passed through.
     * @throws org.dspace.authorize.AuthorizeException passed through.
     * @throws java.io.IOException                     passed through.
     * @throws org.dspace.app.xmlui.utils.UIException  if bitstreamIDs cannot be
     *                                                 parsed.
     */
    public static FlowResult processDeleteBitstreams(Context context, UUID itemID, String[] bitstreamIDs)
        throws SQLException, AuthorizeException, IOException, UIException
    {
        FlowResult result = new FlowResult();
        result.setContinue(false);

        Item item = itemService.find(context, itemID);

        for (String id : bitstreamIDs) {
            String[] parts = id.split("/");

            if (parts.length != 2) {
                throw new UIException("Unable to parse id into bundle and bitstream id: " + id);
            }

            UUID bundleID = UUID.fromString(parts[0]);
            UUID bitstreamID = UUID.fromString(parts[1]);

            Bundle bundle = bundleService.find(context, bundleID);
            Bitstream bitstream = bitstreamService.find(context, bitstreamID);

            bundleService.removeBitstream(context, bundle, bitstream);

            if (bundle.getBitstreams().size() == 0) {
                itemService.removeBundle(context, item, bundle);
            }
        }

        itemService.update(context, item);
        result.setContinue(true);
        result.setOutcome(true);
        result.setMessage(T_bitstream_delete);

        return result;
    }

    public static FlowResult processReorderBitstream(Context context, UUID itemID, Request request)
            throws SQLException, AuthorizeException {
        String submitButton = Util.getSubmitButton(request, "submit_update_order");
        FlowResult result = new FlowResult();
        result.setContinue(false);
        Item item = itemService.find(context, itemID);
        List<Bundle> bundles = item.getBundles();

        for (Bundle bundle : bundles) {
            List<Bitstream> bitstreams = bundle.getBitstreams();
            UUID[] newBitstreamOrder = new UUID[bitstreams.size()];

            if (submitButton.equals("submit_update_order")) {
                for (Bitstream bitstream : bitstreams) {
                    // The order is determined by javascript
                    // For each of our bitstream retrieve the order value
                    int order = Util.getIntParameter(request, "order_" + bitstream.getID());
                    // -1 the order since the order needed to start from one
                    order--;
                    // Place the bitstream identifier in the correct order
                    newBitstreamOrder[order] = bitstream.getID();
                }
            } else {
                // Javascript isn't operational retrieve the value from the hidden field
                // Retrieve the button key
                String inputKey = submitButton.replace("submit_order_", "") + "_value";
                if (inputKey.startsWith(bundle.getID() + "_")) {
                    String[] vals = request.getParameter(inputKey).split(",");
                    for (int i = 0; i < vals.length; i++) {
                        String val = vals[i];
                        newBitstreamOrder[i] = UUID.fromString(val);
                    }
                } else {
                    newBitstreamOrder = null;
                }
            }
            if (newBitstreamOrder != null) {
                // Set the new order in our bundle !
                bundleService.setOrder(context, bundle, newBitstreamOrder);
                bundleService.update(context, bundle);
            }
        }

        result.setContinue(true);
        result.setOutcome(true);
        result.setMessage(T_bitstream_order);

        return result;
    }

    /**
     * processCurateDSO
     *
     * Utility method to process curation tasks submitted via the DSpace GUI.
     *
     * @param context session context.
     * @param itemID  the Item to be curated.
     * @param request user request.
     * @return flow result.
     * @throws org.dspace.authorize.AuthorizeException passed through.
     * @throws java.io.IOException                     passed through.
     * @throws java.sql.SQLException                   passed through.
     */
    public static FlowResult processCurateItem(Context context, UUID itemID, Request request)
            throws AuthorizeException, IOException, SQLException, Exception {
        String task = request.getParameter("curate_task");
        Curator curator = FlowCurationUtils.getCurator(task);
        try {
            Item item = itemService.find(context, itemID);
            if (item != null) {
                // Call curate(context,ID) to ensure a Task Performer (Eperson) is set in
                // Curator
                curator.curate(context, item.getHandle());
            }
            return FlowCurationUtils.getRunFlowResult(task, curator, true);
        } catch (Exception e) {
            curator.setResult(task, e.getMessage());
            return FlowCurationUtils.getRunFlowResult(task, curator, false);
        }
    }

    /**
     * queues curation tasks.
     *
     * @param context session context.
     * @param itemID  the Item to be curated.
     * @param request user request.
     * @return flow result.
     * @throws org.dspace.authorize.AuthorizeException passed through.
     * @throws java.io.IOException                     passed through.
     * @throws java.sql.SQLException                   passed through.
     */
    public static FlowResult processQueueItem(Context context, UUID itemID, Request request)
            throws AuthorizeException, IOException, SQLException, Exception {
        String task = request.getParameter("curate_task");
        Curator curator = FlowCurationUtils.getCurator(task);
        String objId = String.valueOf(itemID);
        String taskQueueName = DSpaceServicesFactory.getInstance().getConfigurationService()
                .getProperty("curate.ui.queuename");
        boolean status = false;
        Item item = itemService.find(context, itemID);
        if (item != null) {
            objId = item.getHandle();
            try {
                curator.queue(context, objId, taskQueueName);
                status = true;
            } catch (IOException ioe) {
                // no-op (the Curator should have already logged any error that occurred)
            }
        }
        return FlowCurationUtils.getQueueFlowResult(task, status, objId, taskQueueName);
    }

    /**
     * Parse the given name into three parts, divided by an _. Each part should
     * represent the schema, element, and qualifier. You are guaranteed that if no
     * qualifier was supplied the third entry is null.
     * 
     * @param name The name to be parsed.
     * @return An array of name parts.
     */
    private static String[] parseName(String name) throws UIException {
        String[] parts = new String[3];

        String[] split = name.split("_");
        if (split.length == 2) {
            parts[0] = split[0];
            parts[1] = split[1];
            parts[2] = null;
        } else if (split.length == 3) {
            parts[0] = split[0];
            parts[1] = split[1];
            parts[2] = split[2];
        } else {
            throw new UIException("Unable to parse metedata field name: " + name);
        }
        return parts;
    }

    private static void processAUETDEmbargoAccessFields(Context context, HttpServletRequest request, Item item, Bitstream bitstream) 
        throws AuthorizeException, IOException, SQLException
    {
        int embargoCreationAnswer = 0;

        clearEmbargoData();

        if (StringUtils.isNotBlank(request.getParameter(AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME))) {
            embargoCreationAnswer = Integer.parseInt(request.getParameter(AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME));

            if (embargoCreationAnswer == 2 || embargoCreationAnswer == 3) {
                String embargoRights = null;
                if (embargoCreationAnswer == 2) {
                    embargoRights = AUETDConstants.EMBARGO_NOT_AUBURN_STR;
                } else if (embargoCreationAnswer == 3) {
                    embargoRights = AUETDConstants.EMBARGO_GLOBAL_STR;
                }

                if (StringUtils.isNotBlank(embargoRights)) {
                    log.debug(LogManager.getHeader(context, "process_auetd_embargo_access_fields", " embargo rights = "+embargoRights));
                    embargoService.createOrModifyEmbargoMetadataValue(context, item, "rights", null, embargoRights);

                    log.debug(LogManager.getHeader(context, "process_auetd_embargo_access_fields", " embargo status = " + AUETDConstants.EMBARGOED));
                    embargoService.createOrModifyEmbargoMetadataValue(context, item, "embargo", "status", AUETDConstants.EMBARGOED);
                }

                if (StringUtils.isNotBlank(request.getParameter(AUETD_EMBARGO_LENGTH_FIELD_NAME))) {
                    String selectedEmbargoLengthValue = request.getParameter(AUETD_EMBARGO_LENGTH_FIELD_NAME);
                    if (StringUtils.isNotBlank(selectedEmbargoLengthValue)) {
                        String embargoLength = embargoService.generateEmbargoLength(context, item, selectedEmbargoLengthValue);
                        log.debug(LogManager.getHeader(context, "process_auetd_embargo_access_fields", " embargo length = "+String.valueOf(embargoLength)));
                        if (StringUtils.isNotBlank(embargoLength)) {
                            embargoService.createOrModifyEmbargoMetadataValue(context, item, "embargo", "length", embargoLength);
                        }
                    }
                }
                embargoService.generateAUETDEmbargoPolicies(context, bitstream, embargoCreationAnswer, item.getOwningCollection());
                embargoService.setEmbargo(context, item);
                setNewEmbargoDataMap(item);

            } else if (embargoCreationAnswer == 1 || embargoCreationAnswer == 0) {
                embargoService.createOrModifyEmbargoMetadataValue(context, item, "embargo", "status", AUETDConstants.NOT_EMBARGOED);
                itemService.clearMetadata(context, item, MetadataSchema.DC_SCHEMA, "embargo", "enddate", Item.ANY);
                itemService.clearMetadata(context, item, MetadataSchema.DC_SCHEMA, "embargo", "length", Item.ANY);
                itemService.clearMetadata(context, item, MetadataSchema.DC_SCHEMA, "rights", null, Item.ANY);

                embargoDataMap.put(AUETD_NEW_EMBARGO_LENGTH_HASH_KEY_NAME, "Deleted");
                embargoDataMap.put(AUETD_NEW_EMBARGO_RIGHTS_HASH_KEY_NAME, "Deleted");
                embargoDataMap.put(AUETD_NEW_EMBARGO_STATUS_HASH_KEY_NAME, "Deleted");
                embargoDataMap.put(AUETD_NEW_EMBARGO_END_DATE_HASH_KEY_NAME, "Deleted");

                authorizeService.generateAutomaticPolicies(context, null, AUETD_EMBARGO_CREATE_QUESTION_FIELD_NAME, bitstream, null);
            }
        }
    }

    private static void notifyEmabargoAdminGroup(Context context, Item item)
        throws AuthorizeException, IOException, SQLException
    {
        List<EPerson> embargoAdminsList = groupService.allMembers(context, groupService.findByName(context,
                configurationService.getProperty("auetd.authorization.embargo.admin.group")));
        Locale defaultLocale = I18nUtil.getDefaultLocale();

        for (EPerson embargoAdmin : embargoAdminsList) {
            try {
                Email email = Email.getEmail(I18nUtil.getEmailFilename(defaultLocale, "embargo_edit_notify"));

                email.addArgument(item.getName()); // Item's title
                email.addArgument(context.getCurrentUser().getFullName()); // Name of user modifying embargo information

                if (embargoDataMap.containsKey(AUETD_PREVIOUS_BITSTREAM_NAME_HASH_KEY_NAME)
                        && StringUtils.isNotBlank(embargoDataMap.get(AUETD_PREVIOUS_BITSTREAM_NAME_HASH_KEY_NAME).toString())) {
                    email.addArgument(embargoDataMap.get(AUETD_PREVIOUS_BITSTREAM_NAME_HASH_KEY_NAME).toString());
                }

                if (embargoDataMap.containsKey(AUETD_NEW_BITSTREAM_NAME_HASH_KEY_NAME)
                        && StringUtils.isNotBlank(embargoDataMap.get(AUETD_NEW_BITSTREAM_NAME_HASH_KEY_NAME).toString())) {
                    email.addArgument(embargoDataMap.get(AUETD_NEW_BITSTREAM_NAME_HASH_KEY_NAME).toString());
                }

                if (embargoDataMap.containsKey(AUETD_PREVIOUS_EMBARGO_STATUS_HASH_KEY_NAME)
                        && StringUtils.isNotBlank(embargoDataMap.get(AUETD_PREVIOUS_EMBARGO_STATUS_HASH_KEY_NAME).toString())) {
                    email.addArgument(embargoDataMap.get(AUETD_PREVIOUS_EMBARGO_STATUS_HASH_KEY_NAME).toString());
                } else {
                    email.addArgument("N/A");
                }

                if (embargoDataMap.containsKey(AUETD_NEW_EMBARGO_STATUS_HASH_KEY_NAME)
                        && StringUtils.isNotBlank(embargoDataMap.get(AUETD_NEW_EMBARGO_STATUS_HASH_KEY_NAME).toString())) {
                    email.addArgument(embargoDataMap.get(AUETD_NEW_EMBARGO_STATUS_HASH_KEY_NAME).toString());
                } else {
                    email.addArgument("N/A");
                }

                if (embargoDataMap.containsKey(AUETD_PREVIOUS_EMBARGO_END_DATE_HASH_KEY_NAME)
                        && StringUtils.isNotBlank(embargoDataMap.get(AUETD_PREVIOUS_EMBARGO_END_DATE_HASH_KEY_NAME).toString())) {
                    email.addArgument(embargoDataMap.get(AUETD_PREVIOUS_EMBARGO_END_DATE_HASH_KEY_NAME).toString());
                } else {
                    email.addArgument("N/A");
                }

                if (embargoDataMap.containsKey(AUETD_NEW_EMBARGO_END_DATE_HASH_KEY_NAME)
                        && StringUtils.isNotBlank(embargoDataMap.get(AUETD_NEW_EMBARGO_END_DATE_HASH_KEY_NAME).toString())) {
                    email.addArgument(embargoDataMap.get(AUETD_NEW_EMBARGO_END_DATE_HASH_KEY_NAME).toString());
                } else {
                    email.addArgument("N/A");
                }

                if (embargoDataMap.containsKey(AUETD_PREVIOUS_EMBARGO_LENGTH_HASH_KEY_NAME)
                        && StringUtils.isNotBlank(embargoDataMap.get(AUETD_PREVIOUS_EMBARGO_LENGTH_HASH_KEY_NAME).toString())) {
                    email.addArgument(embargoDataMap.get(AUETD_PREVIOUS_EMBARGO_LENGTH_HASH_KEY_NAME).toString());
                } else {
                    email.addArgument("N/A");
                }

                if (embargoDataMap.containsKey(AUETD_NEW_EMBARGO_LENGTH_HASH_KEY_NAME)
                        && StringUtils.isNotBlank(embargoDataMap.get(AUETD_NEW_EMBARGO_LENGTH_HASH_KEY_NAME).toString())) {
                    email.addArgument(embargoDataMap.get(AUETD_NEW_EMBARGO_LENGTH_HASH_KEY_NAME).toString());
                } else {
                    email.addArgument("N/A");
                }

                if (embargoDataMap.containsKey(AUETD_PREVIOUS_EMBARGO_RIGHTS_HASH_KEY_NAME)
                        && StringUtils.isNotBlank(embargoDataMap.get(AUETD_PREVIOUS_EMBARGO_RIGHTS_HASH_KEY_NAME).toString())) {
                    email.addArgument(embargoDataMap.get(AUETD_PREVIOUS_EMBARGO_RIGHTS_HASH_KEY_NAME));
                } else {
                    email.addArgument("N/A");
                }

                if (embargoDataMap.containsKey(AUETD_NEW_EMBARGO_RIGHTS_HASH_KEY_NAME)
                        && StringUtils.isNotBlank(embargoDataMap.get(AUETD_NEW_EMBARGO_RIGHTS_HASH_KEY_NAME).toString())) {
                    email.addArgument(embargoDataMap.get(AUETD_NEW_EMBARGO_RIGHTS_HASH_KEY_NAME).toString());
                } else {
                    email.addArgument("N/A");
                }

                String itemLinkURL = configurationService.getProperty("dspace.url") + "/handle/" + item.getHandle();

                email.addArgument(itemLinkURL);

                log.debug(LogManager.getHeader(context, "admin_embargo_edit_notify_email",
                        "Email contents = " + email.toString()));

                if (StringUtils.isNotBlank(embargoAdmin.getEmail())) {
                    email.addRecipient(embargoAdmin.getEmail());
                    email.send();
                }
            } catch (MessagingException ex) {
                log.error(LogManager.getHeader(context, "embargo_edit_notify_admins", ex.getMessage()));
                ex.printStackTrace();
            }
        }
    }

    private static void createEmbargoEditProvenanceMessage(Context context, Item item) throws AuthorizeException, IOException, SQLException
    {
        StringBuilder embargoEditMessage = new StringBuilder();
        String currentDT = LocalDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        embargoEditMessage.append("Information for this item's bitstream was adjusted by ").append(context.getCurrentUser().getFullName()).append(" (")
            .append(context.getCurrentUser().getEmail()).append(") on ").append(currentDT).append("\n")
            .append("Changes include: ").append("\n");

        if (embargoDataMap.containsKey(AUETD_PREVIOUS_BITSTREAM_NAME_HASH_KEY_NAME)) {
            embargoEditMessage.append("Bitstream's Name - Previous: ").append(embargoDataMap.get(AUETD_PREVIOUS_BITSTREAM_NAME_HASH_KEY_NAME)).append("\n");
        }

        if (embargoDataMap.containsKey(AUETD_PREVIOUS_BITSTREAM_NAME_HASH_KEY_NAME)
                && StringUtils.isNotBlank(embargoDataMap.get(AUETD_PREVIOUS_BITSTREAM_NAME_HASH_KEY_NAME).toString())) {
            embargoEditMessage.append("Bitstream's Name - Previous: ").append(embargoDataMap.get(AUETD_PREVIOUS_BITSTREAM_NAME_HASH_KEY_NAME).toString()).append("\n");
        }

        if (embargoDataMap.containsKey(AUETD_NEW_BITSTREAM_NAME_HASH_KEY_NAME)
                && StringUtils.isNotBlank(embargoDataMap.get(AUETD_NEW_BITSTREAM_NAME_HASH_KEY_NAME).toString())) {
            embargoEditMessage.append("Bitstream's Name - New: ").append(embargoDataMap.get(AUETD_NEW_BITSTREAM_NAME_HASH_KEY_NAME).toString()).append("\n");
        }

        if (embargoDataMap.containsKey(AUETD_PREVIOUS_EMBARGO_STATUS_HASH_KEY_NAME)
                && StringUtils.isNotBlank(embargoDataMap.get(AUETD_PREVIOUS_EMBARGO_STATUS_HASH_KEY_NAME).toString())) {
            embargoEditMessage.append("Embargo Status - Previous: ").append(embargoDataMap.get(AUETD_PREVIOUS_EMBARGO_STATUS_HASH_KEY_NAME).toString()).append("\n");
        }

        if (embargoDataMap.containsKey(AUETD_NEW_EMBARGO_STATUS_HASH_KEY_NAME)
                && StringUtils.isNotBlank(embargoDataMap.get(AUETD_NEW_EMBARGO_STATUS_HASH_KEY_NAME).toString())) {
            embargoEditMessage.append("Embargo Status - New: ").append(embargoDataMap.get(AUETD_NEW_EMBARGO_STATUS_HASH_KEY_NAME).toString()).append("\n");
        }

        if (embargoDataMap.containsKey(AUETD_PREVIOUS_EMBARGO_END_DATE_HASH_KEY_NAME)
                && StringUtils.isNotBlank(embargoDataMap.get(AUETD_PREVIOUS_EMBARGO_END_DATE_HASH_KEY_NAME).toString())) {
            embargoEditMessage.append("dc.embargo.enddate - Previous: ").append(embargoDataMap.get(AUETD_PREVIOUS_EMBARGO_END_DATE_HASH_KEY_NAME).toString()).append("\n");
        }

        if (embargoDataMap.containsKey(AUETD_NEW_EMBARGO_END_DATE_HASH_KEY_NAME)
                && StringUtils.isNotBlank(embargoDataMap.get(AUETD_NEW_EMBARGO_END_DATE_HASH_KEY_NAME).toString())) {
            embargoEditMessage.append("dc.embargo.enddate - New: ").append(embargoDataMap.get(AUETD_NEW_EMBARGO_END_DATE_HASH_KEY_NAME).toString()).append("\n");
        }

        if (embargoDataMap.containsKey(AUETD_PREVIOUS_EMBARGO_LENGTH_HASH_KEY_NAME)
                && StringUtils.isNotBlank(embargoDataMap.get(AUETD_PREVIOUS_EMBARGO_LENGTH_HASH_KEY_NAME).toString())) {
            embargoEditMessage.append("dc.emabrgo.length - Previous ").append(embargoDataMap.get(AUETD_PREVIOUS_EMBARGO_LENGTH_HASH_KEY_NAME).toString()).append("\n");
        }

        if (embargoDataMap.containsKey(AUETD_NEW_EMBARGO_LENGTH_HASH_KEY_NAME)
                && StringUtils.isNotBlank(embargoDataMap.get(AUETD_NEW_EMBARGO_LENGTH_HASH_KEY_NAME).toString())) {
            embargoEditMessage.append("dc.embargo.length - New ").append(embargoDataMap.get(AUETD_NEW_EMBARGO_LENGTH_HASH_KEY_NAME).toString()).append("\n");
        }

        if (embargoDataMap.containsKey(AUETD_PREVIOUS_EMBARGO_RIGHTS_HASH_KEY_NAME)
                && StringUtils.isNotBlank(embargoDataMap.get(AUETD_PREVIOUS_EMBARGO_RIGHTS_HASH_KEY_NAME).toString())) {
            embargoEditMessage.append("dc.rights - Previous: ").append(embargoDataMap.get(AUETD_PREVIOUS_EMBARGO_RIGHTS_HASH_KEY_NAME).toString()).append("\n");
        }

        if (embargoDataMap.containsKey(AUETD_NEW_EMBARGO_RIGHTS_HASH_KEY_NAME)
                && StringUtils.isNotBlank(embargoDataMap.get(AUETD_NEW_EMBARGO_RIGHTS_HASH_KEY_NAME).toString())) {
            embargoEditMessage.append("dc.rights - New: ").append(embargoDataMap.get(AUETD_NEW_EMBARGO_RIGHTS_HASH_KEY_NAME).toString()).append("\n");
        }

        itemService.addMetadata(context, item, MetadataSchema.DC_SCHEMA, "description", "provenance", "en_US", embargoEditMessage.toString());
    }

    private static void setPreviousEmbargoData(Item item)
    {
        if (item != null) {
            clearEmbargoData();

            List<MetadataValue> embargoEndDateList = itemService.getMetadata(item, MetadataSchema.DC_SCHEMA, "embargo", "enddate", Item.ANY);
            List<MetadataValue> embargoLengthList = itemService.getMetadata(item, MetadataSchema.DC_SCHEMA, "embargo", "length", Item.ANY);
            List<MetadataValue> embargoRightsList = itemService.getMetadata(item, MetadataSchema.DC_SCHEMA, "rights", null, Item.ANY);
            List<MetadataValue> embargoStatusList = itemService.getMetadata(item, MetadataSchema.DC_SCHEMA, "embargo", "status", Item.ANY);

            if (!embargoEndDateList.isEmpty()) {
                embargoDataMap.put(AUETD_PREVIOUS_EMBARGO_END_DATE_HASH_KEY_NAME, embargoEndDateList.get(0).getValue());
            }

            if (!embargoLengthList.isEmpty()) {
                embargoDataMap.put(AUETD_PREVIOUS_EMBARGO_LENGTH_HASH_KEY_NAME, embargoLengthList.get(0).getValue());
            }

            if (!embargoRightsList.isEmpty()) {
                embargoDataMap.put(AUETD_PREVIOUS_EMBARGO_RIGHTS_HASH_KEY_NAME, embargoRightsList.get(0).getValue());
            }

            if (!embargoStatusList.isEmpty()) {
                 embargoDataMap.put(AUETD_PREVIOUS_EMBARGO_STATUS_HASH_KEY_NAME, embargoStatusList.get(0).getValue());
            }
        }
    }

    private static void clearEmbargoData() {

        if (embargoDataMap.containsKey(AUETD_PREVIOUS_EMBARGO_STATUS_HASH_KEY_NAME)) {
            embargoDataMap.remove(AUETD_PREVIOUS_EMBARGO_STATUS_HASH_KEY_NAME);
        }
        if (embargoDataMap.containsKey(AUETD_PREVIOUS_EMBARGO_END_DATE_HASH_KEY_NAME)) {
            embargoDataMap.remove(AUETD_PREVIOUS_EMBARGO_END_DATE_HASH_KEY_NAME);
        }
        if (embargoDataMap.containsKey(AUETD_PREVIOUS_EMBARGO_LENGTH_HASH_KEY_NAME)) {
            embargoDataMap.remove(AUETD_PREVIOUS_EMBARGO_LENGTH_HASH_KEY_NAME);
        }
        if (embargoDataMap.containsKey(AUETD_PREVIOUS_EMBARGO_RIGHTS_HASH_KEY_NAME)) {
            embargoDataMap.remove(AUETD_PREVIOUS_EMBARGO_RIGHTS_HASH_KEY_NAME);
        }

        if (embargoDataMap.containsKey(AUETD_NEW_EMBARGO_LENGTH_HASH_KEY_NAME)) {
            embargoDataMap.remove(AUETD_NEW_EMBARGO_LENGTH_HASH_KEY_NAME);
        }
        if (embargoDataMap.containsKey(AUETD_NEW_EMBARGO_RIGHTS_HASH_KEY_NAME)) {
            embargoDataMap.remove(AUETD_NEW_EMBARGO_RIGHTS_HASH_KEY_NAME);
        }
        if (embargoDataMap.containsKey(AUETD_NEW_EMBARGO_STATUS_HASH_KEY_NAME)) {
            embargoDataMap.remove(AUETD_NEW_EMBARGO_STATUS_HASH_KEY_NAME);
        }
        if (embargoDataMap.containsKey(AUETD_NEW_EMBARGO_END_DATE_HASH_KEY_NAME)) {
            embargoDataMap.remove(AUETD_NEW_EMBARGO_END_DATE_HASH_KEY_NAME);
        }
    }

    private static void setNewEmbargoDataMap(Item item)
    {  
        clearEmbargoData();

        if (item != null) {
            List<MetadataValue> embargoEndDateList = itemService.getMetadata(item, MetadataSchema.DC_SCHEMA, "embargo", "enddate", Item.ANY);
            List<MetadataValue> embargoLengthList = itemService.getMetadata(item, MetadataSchema.DC_SCHEMA, "embargo", "length", Item.ANY);
            List<MetadataValue> embargoRightsList = itemService.getMetadata(item, MetadataSchema.DC_SCHEMA, "rights", null, Item.ANY);
            List<MetadataValue> embargoStatusList = itemService.getMetadata(item, MetadataSchema.DC_SCHEMA, "embargo", "status", Item.ANY);

            if (!embargoEndDateList.isEmpty()) {
                embargoDataMap.put(AUETD_NEW_EMBARGO_END_DATE_HASH_KEY_NAME, embargoEndDateList.get(0).getValue());
            }

            if (!embargoLengthList.isEmpty()) {
                embargoDataMap.put(AUETD_NEW_EMBARGO_LENGTH_HASH_KEY_NAME, embargoLengthList.get(0).getValue());
            }

            if (!embargoRightsList.isEmpty()) {
                embargoDataMap.put(AUETD_NEW_EMBARGO_RIGHTS_HASH_KEY_NAME, embargoRightsList.get(0).getValue());
            }

            if (!embargoStatusList.isEmpty()) {
                 embargoDataMap.put(AUETD_NEW_EMBARGO_STATUS_HASH_KEY_NAME, embargoStatusList.get(0).getValue());
            }
        }
    }

    public static boolean embargoPeriodHasPassed(Item item) throws AuthorizeException, IOException, SQLException
    {
        List<MetadataValue> accessionedDateList = itemService.getMetadata(item, MetadataSchema.DC_SCHEMA, "date", "accessioned", Item.ANY);
        String accessionedDateMDV = null;
        LocalDateTime accessionedDateLDT = null;

        if (!accessionedDateList.isEmpty()) {
            accessionedDateMDV = accessionedDateList.get(0).getValue();
        }

        if (StringUtils.isNotBlank(accessionedDateMDV)) {
            CharSequence accessionedDateCS = accessionedDateMDV.subSequence(0, accessionedDateMDV.length()-1);
            accessionedDateLDT = LocalDateTime.parse(accessionedDateCS, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }

        LocalDateTime currentLDT = LocalDateTime.now();
        LocalDateTime finalEmbargoEndDateLDTYears = null;
        if (accessionedDateLDT != null) {
           finalEmbargoEndDateLDTYears = accessionedDateLDT.plusYears(configurationService.getLongProperty("auetd.embargo.length.max.years"));
        }
        

        return currentLDT.isAfter(finalEmbargoEndDateLDTYears);
    }
}
