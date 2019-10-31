/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.embargo;

import java.io.IOException;
import java.sql.SQLException;
import java.time.temporal.ChronoUnit;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.content.*;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.MetadataValueService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.core.service.PluginService;
import org.dspace.embargo.service.EmbargoService;
import org.dspace.eperson.Group;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Public interface to the embargo subsystem.
 * <p>
 * Configuration properties: (with examples)
 * {@code
 *   # DC metadata field to hold the user-supplied embargo terms
 *   embargo.field.terms = dc.embargo.terms
 *   # DC metadata field to hold computed "lift date" of embargo
 *   embargo.field.lift = dc.date.available
 *   # String to indicate indefinite (forever) embargo in terms
 *   embargo.terms.open = Indefinite
 *   # implementation of embargo setter plugin
 *   plugin.single.org.dspace.embargo.EmbargoSetter = edu.my.Setter
 *   # implementation of embargo lifter plugin
 *   plugin.single.org.dspace.embargo.EmbargoLifter = edu.my.Lifter
 * }
 * @author Larry Stone
 * @author Richard Rodgers
 */
public class EmbargoServiceImpl implements EmbargoService
{

    /** log4j category */
    private final Logger log = Logger.getLogger(EmbargoServiceImpl.class);

    // Metadata field components for user-supplied embargo terms
    // set from the DSpace configuration by init()
    protected String terms_schema = null;
    protected String terms_element = null;
    protected String terms_qualifier = null;

    // Metadata field components for lift date, encoded as a DCDate
    // set from the DSpace configuration by init()
    protected String lift_schema = null;
    protected String lift_element = null;
    protected String lift_qualifier = null;

    // plugin implementations
    // set from the DSpace configuration by init()
    protected EmbargoSetter setter = null;
    protected EmbargoLifter lifter = null;

    @Autowired(required = true)
    protected ItemService itemService;

    @Autowired(required = true)
    protected ConfigurationService configurationService;

    @Autowired(required = true)
    protected PluginService pluginService;

    // Custom property declarations
    @Autowired(required = true)
    protected AuthorizeService authorizeService;

    @Autowired(required = true)
    protected BitstreamService bitstreamService;

    @Autowired(required = true)
    protected MetadataValueService metadataValueService;

    @Autowired(required = true)
    protected ResourcePolicyService resourcePolicyService;

    // Custom Constant Assignments
    public static final String EMBARGOED = "EMBARGOED";
    public static final String NOT_EMBARGOED = "NOT_EMBARGOED";
    public static final String EMBARGO_NOT_AUBURN_STR = "EMBARGO_NOT_AUBURN";
    public static final String EMBARGO_GLOBAL_STR = "EMBARGO_GLOBAL";

    protected EmbargoServiceImpl()
    {

    }

    @Override
    public void setEmbargo(Context context, Item item)
        throws SQLException, AuthorizeException
    {
        try {
            context.turnOffAuthorisationSystem();
            /*itemService.clearMetadata(context, item, lift_schema, lift_element, lift_qualifier, Item.ANY);
            itemService.addMetadata(context, item, lift_schema, lift_element, lift_qualifier, null, slift);
            log.info("Set embargo on Item "+item.getHandle()+", expires on: "+slift);*/

            //setter.setEmbargo(context, item);
            Bitstream bitstream = getItemBitstream(context, item);
            Instant embargoEndDateInstant = getEmbargoEndDate(context, item);

            if (bitstream != null && embargoEndDateInstant != null) {
                LocalDateTime embargoEndDateLDT = LocalDateTime.ofInstant(embargoEndDateInstant, ZoneId.systemDefault());
                List<ResourcePolicy> bitstreamResourcePolicyList = authorizeService.getPolicies(context, bitstream);

                log.debug(LogManager.getHeader(context, "setting_auetd_embargo ", " Date.from(embargoEndDateInstant).toString() = "+Date.from(embargoEndDateInstant).toString()));

                for (ResourcePolicy rp : bitstreamResourcePolicyList) {
                    log.debug(LogManager.getHeader(context, "setting_auetd_embargo ", " rp.id = "+String.valueOf(rp.getID())));
                    rp.setEndDate(Date.from(embargoEndDateInstant));

                    resourcePolicyService.update(context, rp);

                    log.debug(LogManager.getHeader(context, "setting_auetd_embargo ", " rp.enddate = "+rp.getEndDate().toInstant().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))));
                }

                String embargoEndDateMDVStr = embargoEndDateLDT.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                log.debug(LogManager.getHeader(context, "setting_auetd_embargo ", " embargoEndDate = "+embargoEndDateMDVStr));

                createOrModifyEmbargoMetadataValue(context, item, "embargo", "enddate", embargoEndDateMDVStr);

                itemService.update(context, item);
            }
        } catch (DateTimeException | IOException e) {
            // TODO Auto-generated catch block
            log.error(LogManager.getHeader(context, "setting_auetd_embargo ", e.getMessage()));
            e.printStackTrace();
        }
        finally {
            context.restoreAuthSystemState();
        }
    }

    @Override
    public DCDate getEmbargoTermsAsDate(Context context, Item item)
        throws SQLException, AuthorizeException
    {
        /*List<MetadataValue> terms = itemService.getMetadata(item, terms_schema, terms_element,
                terms_qualifier, Item.ANY);

        DCDate result = null;

        // Its poor form to blindly use an object that could be null...
        if (terms == null)
            return null;

        result = setter.parseTerms(context, item,
                terms.size() > 0 ? terms.get(0).getValue() : null);

        if (result == null)
            return null;

        // new DCDate(non-date String) means toDate() will return null
        Date liftDate = result.toDate();
        if (liftDate == null)
        {
            throw new IllegalArgumentException(
                    "Embargo lift date is uninterpretable:  "
                            + result.toString());
        }*/

        /*
         * NOTE: We do not check here for past dates as it can result in errors during AIP restoration. 
         * Therefore, UIs should perform any such date validation on input. See DS-3348
         */
        //return result;
        return null;
    }


    @Override
    public void liftEmbargo(Context context, Item item)
        throws SQLException, AuthorizeException, IOException
    {
        /*
        // Since 3.0 the lift process for all embargoes is performed through the dates on the authorization process (see DS-2588)
        // lifter.liftEmbargo(context, item);
        itemService.clearMetadata(context, item, lift_schema, lift_element, lift_qualifier, Item.ANY);

        // set the dc.date.available value to right now
        itemService.clearMetadata(context, item, MetadataSchema.DC_SCHEMA, "date", "available", Item.ANY);
        itemService.addMetadata(context, item, MetadataSchema.DC_SCHEMA, "date", "available", null, DCDate.getCurrent().toString());

        log.info("Lifting embargo on Item "+item.getHandle());
        itemService.update(context, item);
        */
    }


    /**
     * Initialize the bean (after dependency injection has already taken place).
     * Ensures the configurationService is injected, so that we can
     * get plugins and MD field settings from config.
     * Called by "init-method" in Spring config.
     */
    public void init() throws Exception
    {
        if (terms_schema == null)
        {
            String terms = configurationService.getProperty("embargo.field.terms");
            String lift = configurationService.getProperty("embargo.field.lift");
            if (terms == null || lift == null)
            {
                throw new IllegalStateException("Missing one or more of the required DSpace configuration properties for EmbargoManager, check your configuration file.");
            }
            terms_schema = getSchemaOf(terms);
            terms_element = getElementOf(terms);
            terms_qualifier = getQualifierOf(terms);
            lift_schema = getSchemaOf(lift);
            lift_element = getElementOf(lift);
            lift_qualifier = getQualifierOf(lift);

            setter = (EmbargoSetter)pluginService.getSinglePlugin(EmbargoSetter.class);
            if (setter == null)
            {
                throw new IllegalStateException("The EmbargoSetter plugin was not defined in DSpace configuration.");
            }
            lifter = (EmbargoLifter)pluginService.getSinglePlugin(EmbargoLifter.class);
            if (lifter == null)
            {
                throw new IllegalStateException("The EmbargoLifter plugin was not defined in DSpace configuration.");
            }
        }
    }

    // return the schema part of "schema.element.qualifier" metadata field spec
    protected String getSchemaOf(String field)
    {
        String sa[] = field.split("\\.", 3);
        return sa[0];
    }

    // return the element part of "schema.element.qualifier" metadata field spec, if any
    protected String getElementOf(String field)
    {
        String sa[] = field.split("\\.", 3);
        return sa.length > 1 ? sa[1] : null;
    }

    // return the qualifier part of "schema.element.qualifier" metadata field spec, if any
    protected String getQualifierOf(String field)
    {
        String sa[] = field.split("\\.", 3);
        return sa.length > 2 ? sa[2] : null;
    }

    // return the lift date assigned when embargo was set, or null, if either:
    // it was never under embargo, or the lift date has passed.
    protected DCDate recoverEmbargoDate(Item item) {
        /*DCDate liftDate = null;
        List<MetadataValue> lift = itemService.getMetadata(item, lift_schema, lift_element, lift_qualifier, Item.ANY);
        if (lift.size() > 0)
        {
            liftDate = new DCDate(lift.get(0).getValue());
            // sanity check: do not allow an embargo lift date in the past.
            if (liftDate.toDate().before(new Date()))
            {
                liftDate = null;
            }
        }
        return liftDate;*/
        return null;
    }

    @Override
    public void checkEmbargo(Context context, Item item) throws SQLException, IOException, AuthorizeException {
        setter.checkEmbargo(context, item);
    }

    @Override
    public List<MetadataValue> getLiftMetadata(Context context, Item item)
    {
        return itemService.getMetadata(item, lift_schema, lift_element, lift_qualifier, Item.ANY);
    }

    @Override
    public Iterator<Item> findItemsByLiftMetadata(Context context) throws SQLException, IOException, AuthorizeException {
        return itemService.findByMetadataField(context, lift_schema, lift_element, lift_qualifier, Item.ANY);
    }

    public String getEmbargoMetadataValue(Context context, Item item, String element, String qualifier) throws AuthorizeException, IOException, SQLException
    {
        List<MetadataValue> metadataValueList = itemService.getMetadata(item, MetadataSchema.DC_SCHEMA, element, qualifier, Item.ANY);

        if (metadataValueList.size() > 0) {
            for(MetadataValue metadataValue : metadataValueList) {
                if (StringUtils.isNotBlank(metadataValue.getValue())) {
                    return metadataValue.getValue();
                }
            }
        }
        return null;
    }

    @Override
    public void createOrModifyEmbargoMetadataValue(Context context, Item item, String element, String qualifier, String value) throws AuthorizeException, IOException, SQLException
    {
        if (StringUtils.isNotBlank(getEmbargoMetadataValue(context, item, element, qualifier))) {
            itemService.clearMetadata(context, item, MetadataSchema.DC_SCHEMA, element, qualifier, Item.ANY);
            itemService.addMetadata(context, item, MetadataSchema.DC_SCHEMA, element, qualifier, "en_US", value);
        } else {
            if (StringUtils.isNotBlank(element) && StringUtils.isNotBlank(value)) {
                itemService.addMetadata(context, item, MetadataSchema.DC_SCHEMA, element, qualifier, "en_US", value);
            }
        }
    }

    @Override
    public String generateEmbargoLength(Context context, Item item, String selectedLength)
        throws AuthorizeException, IOException, SQLException
    {
        long months = 0;
        String lengthStr = null;

        if (StringUtils.isNotBlank(selectedLength)) {
            int length = 0;
            length = Integer.parseInt(selectedLength);

            months = 12 * length;
        } else {
            Instant embargoEndDateInstant = getEmbargoEndDate(context, item);

            if(embargoEndDateInstant != null) {
                LocalDate embargoStartDate = LocalDate.parse(getEmbargoMetadataValue(context, item, "date", "accessioned"));
                LocalDate embargoEndDate = LocalDate.from(embargoEndDateInstant);

                months = ChronoUnit.MONTHS.between(embargoStartDate, embargoEndDate);
            }
        }

        if(months > 0) {
            lengthStr = "MONTHS_WITHHELD:"+months;
        }
        return lengthStr;
    }

    public Instant getEmbargoEndDate(Context context, Item item) throws AuthorizeException, DateTimeException, IOException, SQLException
    {
        Instant embargoEndDate = null;

        embargoEndDate = generateNewEmbargoEndDate(context, item);
        log.debug(LogManager.getHeader(context, "get_embargo_enddate", " Is embargoEndDate null "+String.valueOf(embargoEndDate == null)));

        if (embargoEndDate != null) {
            LocalDateTime embargoEndDateLDT = LocalDateTime.ofInstant(embargoEndDate, ZoneId.systemDefault());
            log.info(LogManager.getHeader(context, "get_embargo_enddate", " embargoEndDate = "+embargoEndDateLDT.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))));
        }

        return embargoEndDate;
    }

    private Instant generateNewEmbargoEndDate(Context context, Item item) throws AuthorizeException, IOException, SQLException
    {
        long lengthNum = 0;
        Instant calculatedEmbargoEndDate = null;
        String accessionedDateMDV = null;
        Instant startDate = null;
        LocalDateTime accessionedDateLDT = null;

        log.debug(LogManager.getHeader(context, "calculating_embargo_enddate", "Item id pre embargo setting = "+item.getID().toString()));

        List<MetadataValue> accessionedDateList = itemService.getMetadata(item, MetadataSchema.DC_SCHEMA, "date", "accessioned", Item.ANY);

        if (accessionedDateList != null && accessionedDateList.size() > 0) {
            accessionedDateMDV = accessionedDateList.get(0).getValue();
        }

        if (StringUtils.isNotBlank(accessionedDateMDV)) {
            CharSequence accessionedDateCS = accessionedDateMDV.subSequence(0, accessionedDateMDV.length()-1);
            accessionedDateLDT = LocalDateTime.parse(accessionedDateCS, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            startDate = LocalDateTime.of(accessionedDateLDT.getYear(), accessionedDateLDT.getMonthValue(), accessionedDateLDT.getDayOfMonth(), accessionedDateLDT.getHour(), accessionedDateLDT.getMinute(), accessionedDateLDT.getSecond()).atZone(ZoneId.systemDefault()).toInstant();
        } else {
            startDate = Instant.now();
        }

        List<MetadataValue> embargoLengthList = itemService.getMetadata(item, MetadataSchema.DC_SCHEMA, "embargo", "length", "en_US");
        log.debug(LogManager.getHeader(context, "calculating_embargo_enddate ", " Is embargoLengthList null? "+String.valueOf(embargoLengthList == null)));
        log.debug(LogManager.getHeader(context, "calculating_embargo_enddate ", " Size of embargoLengthList = "+String.valueOf(embargoLengthList.size())));

        if (embargoLengthList != null && embargoLengthList.size() > 0) {

            log.debug(LogManager.getHeader(context, "calculating_embargo_enddate ", " Is dc.embargo.length null? "+String.valueOf(StringUtils.isNotBlank(embargoLengthList.get(0).getValue()))));
            log.debug(LogManager.getHeader(context, "calculating_embargo_enddate ", " dc.embargo.length = "+embargoLengthList.get(0).getValue()));

            ArrayList<String> embargoLengths = new ArrayList<String>();
            embargoLengths.addAll(Arrays.asList(embargoLengthList.get(0).getValue().split(":")));
            log.debug(LogManager.getHeader(context, "calculating_embargo_enddate ", " Size of embargoLengths = "+String.valueOf(embargoLengths.size())));
            if (embargoLengths.size() > 0) {
                log.debug(LogManager.getHeader(context, "calculating_embargo_enddate ", " embargoLengths[0] = "+embargoLengths.get(0)));
                log.debug(LogManager.getHeader(context, "calculating_embargo_enddate ", " embargoLengths[1] = "+embargoLengths.get(1)));
                lengthNum = Long.parseLong(embargoLengths.get(1));
            }
        }
        log.debug(LogManager.getHeader(context, "calculating_embargo_enddate ", " lengthNum = "+String.valueOf(lengthNum)));

        if (lengthNum > 0) {
            try {
                LocalDateTime embargoEndDateLDT = LocalDateTime.ofInstant(startDate, ZoneId.systemDefault());
                calculatedEmbargoEndDate = embargoEndDateLDT.atZone(ZoneId.systemDefault()).plusMonths(lengthNum).toInstant();
            } catch (DateTimeException dtEx) {
                log.error(LogManager.getHeader(context, "calculating_embargo_enddate", " Could not generate an "));
                dtEx.printStackTrace();
                System.exit(1);
            }
        }

        log.debug(LogManager.getHeader(context, "calculating_embargo_enddate ", " Is calculatedEmbargoEndDate null "+String.valueOf(calculatedEmbargoEndDate == null)));

        return calculatedEmbargoEndDate;
    }

    public long generateEmbargoEndDateTimeStamp(Context context, Item item) throws AuthorizeException, IOException, SQLException
    {
        String embargoEndDateMDV = null;
        String accessionedDateMDV = null;
        LocalDate embargoEndDateLD = null;
        LocalDateTime accessionedDateLDT = null;
        long embargoEndDateTimeStamp = 0;

        if (StringUtils.isNotBlank(getEmbargoMetadataValue(context, item, "embargo", "enddate"))) {
            embargoEndDateMDV = getEmbargoMetadataValue(context, item, "embargo", "enddate");
        }

        if (StringUtils.isNotBlank(getEmbargoMetadataValue(context, item, "date", "accessioned"))) {
            accessionedDateMDV = getEmbargoMetadataValue(context, item, "date", "accessioned");
        }

        if (StringUtils.isNotBlank(accessionedDateMDV) && StringUtils.isNotBlank(embargoEndDateMDV)) {
            CharSequence accessionedDateCS = accessionedDateMDV.subSequence(0, accessionedDateMDV.length()-1);
            accessionedDateLDT = LocalDateTime.parse(accessionedDateCS, DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            CharSequence embargoEndDateCS = embargoEndDateMDV.subSequence(0, embargoEndDateMDV.length());
            embargoEndDateLD = LocalDate.parse(embargoEndDateCS, DateTimeFormatter.ISO_LOCAL_DATE);

            LocalDateTime embargoEndDateLDT = LocalDateTime.of(embargoEndDateLD.getYear(), embargoEndDateLD.getMonthValue(), embargoEndDateLD.getDayOfMonth(), accessionedDateLDT.getHour(), accessionedDateLDT.getMinute(), accessionedDateLDT.getSecond());
            embargoEndDateTimeStamp = embargoEndDateLDT.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }

        return embargoEndDateTimeStamp;
    }

    @Override
    public void generateAUETDEmbargoPolicies(Context context, DSpaceObject dso, int embargoType, Collection owningCollection)
        throws AuthorizeException, IOException, SQLException
    {
        Item item = null;
        List<ResourcePolicy> owningCollReadResourcePolicyList = null;
        Bitstream bitstream = null;

        if (embargoType < 0 || embargoType > 3) {
            log.error(LogManager.getHeader(context, "generate_auetd_embargo_policies ", " The supplied embargo type value "+String.valueOf(embargoType)+" is invalid."));
            throw new IllegalArgumentException("The supplied embargo type value "+String.valueOf(embargoType)+" is invalid.");
        }

        if (dso instanceof Bitstream) {
            bitstream = bitstreamService.find(context, dso.getID());
            DSpaceObject parent = bitstreamService.getParentObject(context, bitstream);

            if (parent != null) {
                item = itemService.find(context, parent.getID());
            }
        } else if (dso instanceof Item) {
            item = itemService.find(context, dso.getID());

            bitstream = getItemBitstream(context, item);
        }

        if (bitstream != null) {
            authorizeService.removeAllPolicies(context, bitstream);
        }

        if (owningCollection != null) {
            owningCollReadResourcePolicyList = authorizeService.getPoliciesActionFilter(context, owningCollection, Constants.READ);
        }

        if (owningCollReadResourcePolicyList != null && owningCollReadResourcePolicyList.size() > 0 && bitstream != null) {
            if (embargoType == 2) {
                /**
                 * If the user has chosen to hide the bitstream from the public only
                 * then create a resource policy only for the Anonymous user group.
                 */
                for(ResourcePolicy resourcePolicy : owningCollReadResourcePolicyList) {
                    if(StringUtils.equals(resourcePolicy.getGroup().getName(), Group.ANONYMOUS) && StringUtils.equalsIgnoreCase(resourcePolicy.getRpName(), "Public_Read")) {
                        ResourcePolicy newResourcePolicy = resourcePolicyService.clone(context, resourcePolicy);
                        newResourcePolicy.setdSpaceObject(bitstream);
                        newResourcePolicy.setRpName(resourcePolicy.getRpName());
                        resourcePolicyService.update(context, newResourcePolicy);
                    }
                }
            } else if (embargoType == 3) {
                /**
                 * Else if the submitter has chosen to restrict access
                 * from everyone then create a resource policy record
                 * for each policy in the policies list.
                 */
                for(ResourcePolicy resourcePolicy : owningCollReadResourcePolicyList) {
                    ResourcePolicy newResourcePolicy = resourcePolicyService.clone(context, resourcePolicy);
                    newResourcePolicy.setdSpaceObject(bitstream);
                    newResourcePolicy.setRpName(resourcePolicy.getRpName());
                    newResourcePolicy.setRpType(resourcePolicy.getRpType());
                    resourcePolicyService.update(context, newResourcePolicy);
                }
            }
        } else {
            log.error(LogManager.getHeader(context, "generate_auetd_embargo_policies ", " Owning collection has no resource policies."));
        }
    }

    private Bitstream getItemBitstream(Context context, Item item)
        throws AuthorizeException, SQLException
    {
        Bitstream localBitstream = null;

        log.debug(LogManager.getHeader(context, "get_item_bitstream ", " Is item null? "+String.valueOf(item == null)));
        log.debug(LogManager.getHeader(context, "get_item_bitstream ", " Item id = "+item.getID().toString()));

        if (item != null) {
            for (Bundle bundle : itemService.getBundles(item, "ORIGINAL")) {
                log.debug(LogManager.getHeader(context, "get_item_bitstream ", " Is bundle null? "+String.valueOf(bundle == null)));
                if (bundle != null) {
                    log.debug(LogManager.getHeader(context, "get_item_bitstream ", " Bundle id = "+bundle.getID().toString()));
                    for (Bitstream bitstream : bundle.getBitstreams()) {
                        localBitstream = bitstream;
                    }
                }
            }
        }

        log.debug(LogManager.getHeader(context, "get_item_bitstream ", "Is localBitstream null? "+String.valueOf(localBitstream == null)));

        if (localBitstream != null) {
            log.debug(LogManager.getHeader(context, "get_item_bitstream ", " Bistream id = "+localBitstream.getID().toString()));
        }

        return localBitstream;
    }
}
