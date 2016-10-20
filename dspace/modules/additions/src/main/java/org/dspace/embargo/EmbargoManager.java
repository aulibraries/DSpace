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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.log4j.Logger;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.*;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.core.PluginManager;
import org.dspace.handle.HandleManager;

/**
 * Public interface to the embargo subsystem.
 * <p>
 * Configuration properties: (with examples)
 *   <br/># DC metadata field to hold the user-supplied embargo terms
 *   <br/>embargo.field.terms = dc.embargo.terms
 *   <br/># DC metadata field to hold computed "lift date" of embargo
 *   <br/>embargo.field.lift = dc.date.available
 *   <br/># String to indicate indefinite (forever) embargo in terms
 *   <br/>embargo.terms.open = Indefinite
 *   <br/># implementation of embargo setter plugin
 *   <br/>plugin.single.org.dspace.embargo.EmbargoSetter = edu.my.Setter
 *   <br/># implementation of embargo lifter plugin
 *   <br/>plugin.single.org.dspace.embargo.EmbargoLifter = edu.my.Lifter
 *
 * @author Larry Stone
 * @author Richard Rodgers
 */
public class EmbargoManager
{
    /** Special date signalling an Item is to be embargoed forever.
     ** The actual date is the first day of the year 10,000 UTC.
     **/
    public static final DCDate FOREVER = new DCDate("10000-01-01");

    /** log4j category */
    private static Logger log = Logger.getLogger(EmbargoManager.class);

    // Metadata field components for user-supplied embargo terms
    // set from the DSpace configuration by init()
    private static String terms_schema = null;
    private static String terms_element = null;
    private static String terms_qualifier = null;

    // Metadata field components for lift date, encoded as a DCDate
    // set from the DSpace configuration by init()
    private static String lift_schema = null;
    private static String lift_element = null;
    private static String lift_qualifier = null;

    // plugin implementations
    // set from the DSpace configuration by init()
    private static EmbargoSetter setter = null;
    private static EmbargoLifter lifter = null;

    /**
     * Put an Item under embargo until the specified lift date.
     * Calls EmbargoSetter plugin to adjust Item access control policies.
     *
     * @param context the DSpace context
     * @param item the item to embargo
     */
    public static void setEmbargo(Context context, Item item)
        throws SQLException, AuthorizeException, IOException
    {
        init();
        try
        {
            context.turnOffAuthorisationSystem();

            setter.setEmbargo(context, item);

            item.update();
            item.updateLastModified();
        }
        finally
        {
            context.restoreAuthSystemState();
        }
    }

    /**
     * Get the embargo lift date for an Item, if any.  This looks for the
     * metadata field configured to hold embargo terms, and gives it
     * to the EmbargoSetter plugin's method to interpret it into
     * an absolute timestamp.  This is intended to be called at the time
     * the Item is installed into the archive.
     * <p>
     * Note that the plugin is *always* called, in case it gets its cue for
     * the embargo date from sources other than, or in addition to, the
     * specified field.
     *
     * @param context the DSpace context
     * @param item the item to embargo
     * @return lift date on which the embargo is to be lifted, or null if none
     */
    public static DCDate getEmbargoTermsAsDate(Context context, Item item)
        throws SQLException, AuthorizeException, IOException
    {
        init();
        /*Metadatum terms[] = item.getMetadata(terms_schema, terms_element,
                terms_qualifier, Item.ANY);

        DCDate result = null;

        // Its poor form to blindly use an object that could be null...
        if (terms == null)
            return null;

        result = setter.parseTerms(context, item,
                terms.length > 0 ? terms[0].value : null);

        if (result == null)
            return null;

        // new DCDate(non-date String) means toDate() will return null
        Date liftDate = result.toDate();
        if (liftDate == null)
        {
            throw new IllegalArgumentException(
                    "Embargo lift date is uninterpretable:  "
                            + result.toString());
        }

        // sanity check: do not allow an embargo lift date in the past.
        if (liftDate.before(new Date()))
        {
            throw new IllegalArgumentException(
                    "Embargo lift date must be in the future, but this is in the past: "
                            + result.toString());
        }
        return result;*/

        return null;
    }

    /**
     * Lift the embargo on an item which is assumed to be under embargo.
     * Call the plugin to manage permissions in its own way, then delete
     * the administrative metadata fields that dictated embargo date.
     *
     * @param context the DSpace context
     * @param item the item on which to lift the embargo
     */
    public static void liftEmbargo(Context context, Item item)
        throws SQLException, AuthorizeException, IOException
    {
        init();

        // new version of Embargo policies remain in place.
        lifter.liftEmbargo(context, item);
        /*item.clearMetadata(lift_schema, lift_element, lift_qualifier, Item.ANY);

        // set the dc.date.available value to right now
        item.clearMetadata(MetadataSchema.DC_SCHEMA, "date", "available", Item.ANY);
        item.addMetadata(MetadataSchema.DC_SCHEMA, "date", "available", null, DCDate.getCurrent().toString());

        log.info("Lifting embargo on Item "+item.getHandle());*/
        //item.update();
    }

    /**
     * Command-line service to scan for every Item with an expired embargo,
     * and then lift that embargo.
     * <p>
     * Options:
     * <dl>
     *   <dt>-c,--check</dt>
     *   <dd>         Function: ONLY check the state of embargoed Items, do
     *                      NOT lift any embargoes.</dd>
     *   <dt>-h,--help</dt>
     *   <dd>         Help.</dd>
     *   <dt>-i,--identifier</dt>
     *   <dd>         Process ONLY this Handle identifier(s), which must be
     *                      an Item.  Can be repeated.</dd>
     *   <dt>-l,--lift</dt>
     *   <dd>         Function: ONLY lift embargoes, do NOT check the state
     *                      of any embargoed Items.</dd>
     *   <dt>-n,--dryrun</dt>
     *   <dd>         Do not change anything in the data model; print
     *                      message instead.</dd>
     *   <dt>-v,--verbose</dt>
     *   <dd>         Print a line describing action taken for each
     *                      embargoed Item found.</dd>
     *   <dt>-q,--quiet</dt>
     *   <dd>         No output except upon error.</dd>
     * </dl>
     */
    public static void main(String argv[])
    {

        init();
        int status = 0;

        Options options = new Options();
        options.addOption("v", "verbose", false,
                "Print a line describing action taken for each embargoed Item found.");
        options.addOption("q", "quiet", false,
                "Do not print anything except for errors.");
        options.addOption("n", "dryrun", false,
                "Do not change anything in the data model, print message instead.");
        options.addOption("i", "identifier", true,
                        "Process ONLY this Handle identifier(s), which must be an Item.  Can be repeated.");
        options.addOption("c", "check", false,
                        "Function: ONLY check the state of embargoed Items, do NOT lift any embargoes.");
        options.addOption("l", "lift", false,
                        "Function: ONLY lift embargoes, do NOT check the state of any embargoed Items.");

        options.addOption("a", "adjust", false,
                "Function: Adjust bitstreams policies");

        options.addOption("h", "help", false, "help");
        CommandLine line = null;
        try
        {
            line = new PosixParser().parse(options, argv);
        }
        catch(ParseException e)
        {
            System.err.println("Command error: " + e.getMessage());
            new HelpFormatter().printHelp(EmbargoManager.class.getName(), options);
            System.exit(1);
        }

        if (line.hasOption('h'))
        {
            new HelpFormatter().printHelp(EmbargoManager.class.getName(), options);
            System.exit(0);
        }

        // sanity check, --lift and --check are mutually exclusive:
        if (line.hasOption('l') && line.hasOption('c'))
        {
            System.err.println("Command error: --lift and --check are mutually exclusive, try --help for assistance.");
            System.exit(1);
        }

        Context context = null;
        try
        {
            context = new Context();
            context.turnOffAuthorisationSystem();
            Date now = new Date();
             
            // scan items under embargo
            if (line.hasOption('i'))
            {
                for (String handle : line.getOptionValues('i'))
                {
                    DSpaceObject dso = HandleManager.resolveToObject(context, handle);
                    if (dso == null)
                    {
                        System.err.println("Error, cannot resolve handle="+handle+" to a DSpace Item.");
                        status = 1;
                    }
                    else if (dso.getType() != Constants.ITEM)
                    {
                        System.err.println("Error, the handle="+handle+" is not a DSpace Item.");
                        status = 1;
                    }
                    else
                    {
                        if (processOneItem(context, (Item)dso, line, now))
                        {
                            status = 1;
                        }
                    }
                }
            }
            else
            {
                ItemIterator ii = Item.findAllUnfiltered(context);
                List<Item> culledItemList = new ArrayList<>();

                while (ii.hasNext())
                {
                    Item nextItem = ii.next();

                    DateTime liftDate = null;

                    // if the item is already discoverable then
                    // we continue on to the next item
                    if(nextItem.isDiscoverable())
                    {
                        continue;
                    }

                    List<ResourcePolicy> itemRPList = AuthorizeManager.findPoliciesByDSOAndType(context, nextItem, ResourcePolicy.TYPE_CUSTOM);

                    if(!itemRPList.isEmpty())
                    {
                        ResourcePolicy rp = itemRPList.get(0);

                        liftDate = new DateTime(rp.getStartDate());
                    }

                    if(liftDate != null)
                    {
                        // only add items to the culledItemList if the
                        // item's enddate year, month and day match
                        // those of the current time. These checks
                        // help limit the size of the list to
                        // improve performance and
                        if(liftDate.getYear() == now.getYear())
                        {
                            if(liftDate.getMonthOfYear() == now.getMonthOfYear())
                            {
                                if(!culledItemList.contains(nextItem))
                                {
                                    culledItemList.add(nextItem);
                                }
                            }
                        }
                    }
                }

                if(!culledItemList.isEmpty())
                {
                    System.out.println("Number of Items to Process: "+String.valueOf(culledItemList.size()));

                    Collections.sort(culledItemList, new Comparator<Item>()
                    {
                        @Override
                        public int compare(Item item1, Item item2)
                        {
                            int item1ID = item1.getID();
                            int item2ID = item2.getID();

                            return (item1ID < item2ID ? -1 : (item1ID == item2ID ? 0 : 1));
                        }
                    });
                    for(Item culledItem : culledItemList)
                    {
                        if(processOneItem(context, culledItem, line, null))
                        {
                            status = 1;
                        }
                    }
                }
                else
                {
                    System.out.println("NO items need their embargos processed today.");
                }
            }
            log.debug("Cache size at end = "+context.getCacheSize());
            context.complete();
            context = null;
        }
        catch (Exception e)
        {
            System.err.println("ERROR, got exception: "+e);
            e.printStackTrace();
            status = 1;
        }
        finally
        {
            if (context != null)
            {
                try
                {
                    context.abort();
                }
                catch (Exception e)
                {
                }
            }
        }
        System.exit(status);
    }

    // lift or check embargo on one Item, handle exceptions
    // return false on success, true if there was fatal exception.
    private static boolean processOneItem(Context context, Item item, CommandLine line, Date now)
        throws Exception
    {
        if (line.hasOption('a'))
            {
                setter.setEmbargo(context, item);
            }
            else
            {
                if (line.hasOption('n'))
                {

                    if (!line.hasOption('q'))
                    {
                        //System.out.println("DRY RUN: would have lifted embargo from Item handle=" + item.getHandle() + ", lift date=" +liftDate.toString());
                        //System.out.println("DRY RUN: would have lifted embargo from Item handle=" + item.getHandle() + ", lift date=" +dateAvailable.toString());
                    }

                    log.info(LogManager.getHeader(context, "Checking Embargo", "******************* Checking Embargo Settings of Item "+item.getHandle()+" ************************* "));

                    setter.checkEmbargo(context, item);
                }
                else if (!line.hasOption('c'))
                {
                    System.out.println("");
                    System.out.println("Pre-Lift information for item "+item.getID());
                    System.out.println("Item's Name: \""+item.getName()+"\"");
                    System.out.println("Item's Handle: "+item.getHandle());
                    System.out.println("Item's Archived: "+item.isArchived());
                    System.out.println("Item's Withdrawn/Private:  "+item.isWithdrawn());
                    System.out.println("Item's Discoverable:  "+item.isDiscoverable());
                    System.out.println("-----------------------------------------------------");
                    System.out.println("Item's Embargo Metadata Information");
                    System.out.println("-----------------------------------------------------");

                    if(getEmbargoEndDateMDV(context, item) != null)
                    {
                        System.out.println("Item's Embargo End Date:  "+getEmbargoEndDateMDV(context, item));
                    }
                    System.out.println("-----------------------------------------------------");
                    System.out.println("Action: Lifting Embargo");
                    System.out.println("-----------------------------------------------------");

                    log.info(LogManager.getHeader(context, "Lifting Embargo", "******************* Lifting Embargo on Item "+item.getHandle()+" ************************* "));
                    liftEmbargo(context, item);

                    System.out.println("");
                    System.out.println("Post-Lift information for item "+item.getID());
                    System.out.println("Item's Name: \""+item.getName()+"\"");
                    System.out.println("Item's Handle: "+item.getHandle());
                    System.out.println("Item's Archived: "+item.isArchived());
                    System.out.println("Item's Withdrawn/Private:  "+item.isWithdrawn());
                    System.out.println("Item's Discoverable:  "+item.isDiscoverable());
                    System.out.println("-----------------------------------------------------");
                    System.out.println("");
                    System.out.println("=====================================================");
                }
                else if (!line.hasOption('l'))
                {
                    System.out.println("Checking Embargo");
                    System.out.println("-----------------------------------------------------");

                    if (line.hasOption('v'))
                    {
                        //System.out.println("Checking current embargo on Item handle=" + item.getHandle() + ", lift date=" + liftDate.toString());
                        //System.out.println("Checking current embargo on Item handle=" + item.getHandle() + ", lift date=" + dateAvailable.toString());
                    }

                    log.info(LogManager.getHeader(context, "Checking Embargo", "******************* Checking Embargo Settings of Item "+item.getHandle()+" ************************* "));
                    setter.checkEmbargo(context, item);

                    System.out.println("-----------------------------------------------------");
                    System.out.println("");
                    System.out.println("=====================================================");
                }
            }
        }
        catch (Exception e)
        {
            log.error("Failed attempting to lift embargo, item="+item.getHandle()+": ", e);
            System.err.println("Failed attempting to lift embargo, item="+item.getHandle()+": "+ e);
            status = true;
        }

        context.removeCached(item, item.getID());
        return status;
    }



    // initialize - get plugins and MD field settings from config
    private static void init()
    {
        if (terms_schema == null)
        {
            String terms = ConfigurationManager.getProperty("embargo.field.terms");
            String lift = ConfigurationManager.getProperty("embargo.field.lift");
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

            setter = (EmbargoSetter)PluginManager.getSinglePlugin(EmbargoSetter.class);
            if (setter == null)
            {
                throw new IllegalStateException("The EmbargoSetter plugin was not defined in DSpace configuration.");
            }
            lifter = (EmbargoLifter)PluginManager.getSinglePlugin(EmbargoLifter.class);
            if (lifter == null)
            {
                throw new IllegalStateException("The EmbargoLifter plugin was not defined in DSpace configuration.");
            }
        }
    }

    // return the schema part of "schema.element.qualifier" metadata field spec
    private static String getSchemaOf(String field)
    {
        String sa[] = field.split("\\.", 3);
        return sa[0];
    }

    // return the element part of "schema.element.qualifier" metadata field spec, if any
    private static String getElementOf(String field)
    {
        String sa[] = field.split("\\.", 3);
        return sa.length > 1 ? sa[1] : null;
    }

    // return the qualifier part of "schema.element.qualifier" metadata field spec, if any
    private static String getQualifierOf(String field)
    {
        String sa[] = field.split("\\.", 3);
        return sa.length > 2 ? sa[2] : null;
    }

    // return the lift date assigned when embargo was set, or null, if either:
    // it was never under embargo, or the lift date has passed.
    private static DCDate recoverEmbargoDate(Item item) {
        DCDate liftDate = null;
        Metadatum lift[] = item.getMetadata(lift_schema, lift_element, lift_qualifier, Item.ANY);
        if (lift.length > 0)
        {
            liftDate = new DCDate(lift[0].value);
            // sanity check: do not allow an embargo lift date in the past.
            if (liftDate.toDate().before(new Date()))
            {
                liftDate = null;
            }
        }
        return liftDate;
    }

    /**
     * Returns a metadata field's registery id number
     *
     * @param context
     *      Current Dspace context
     * @param element
     *      Element portion of a metadata field's name
     * @param qualifier
     *      Qualifier portion of a metadata field's name
     * @return
     * @throws SQLException
     * @throws IOException
     * @throws AuthorizeException
     */
    public static int getMetadataFieldID(Context context, String element, String qualifier)
        throws SQLException, IOException, AuthorizeException
    {
        MetadataField mdf = null;

        if(element == null || element.isEmpty())
        {
            log.error(LogManager.getHeader(context, "Getting Metadata Field ID", " Must supply the name of an element."));
            return 0;
        }

        mdf = MetadataField.findByElement(context, MetadataSchema.DC_SCHEMA_ID, element, qualifier);

        if(mdf != null)
        {
            return mdf.getFieldID();
        }

        return 0;
    }

    /**
     * Returns an item's metadata field value
     *
     * @param context
     *      Current Dspace context
     * @param item
     *      Current item being worked on.
     * @param element
     *      Element portion of a metadata field's name
     * @param qualifier
     *      Qualifier portion of a metadata field's name
     *
     * @return
     *      Returns a string representation of an item's dc.embargo.length
     *      metadata field value.
     *
     * @throws java.io.IOException
     * @throws java.sql.SQLException
     * @throws org.dspace.authorize.AuthorizeException
     */
    private static String getMetadataFieldValue(Context context, Item item, String element, String qualifier)
        throws AuthorizeException, IOException, SQLException
    {
        int mdfID = getMetadataFieldID(context, element, qualifier);

        List<MetadataValue> mdValList = MetadataValue.findByField(context, mdfID);

        if(!mdValList.isEmpty())
        {
            for(MetadataValue mdVal : mdValList)
            {
                if(mdVal != null)
                {
                    if(mdVal.getResourceId() == item.getID())
                    {
                        return mdVal.getValue();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Prints out information about a resource policy.
     *
     * @param context
     *      Current DSpace context
     * @param rp
     *      Resource policy to print out
     *
     * @throws java.sql.SQLException
     *
     */
    public static void printRPInfo(Context context, ResourcePolicy rp)
        throws SQLException
    {
        DateTimeFormatter dft = DateTimeFormat.forPattern("yyyy-MM-dd");
        String rsrcTypeTxt = null;
        String response = null;

        System.out.println("Policy ID:  "+rp.getID());
        System.out.println("Resource ID:  "+rp.getResourceID());

        log.debug(LogManager.getHeader(context, "Printing Policy Info", " Policy ID = "+rp.getID()));
        log.debug(LogManager.getHeader(context, "Printing Policy Info", " Resource ID = "+rp.getResourceID()));

        DSpaceObject dso = DSpaceObject.find(context, rp.getResourceType(), rp.getResourceID());
        rsrcTypeTxt = dso.getTypeText();

        System.out.println("Resource Type:  "+rsrcTypeTxt.toUpperCase());
        log.debug(LogManager.getHeader(context, "Printing Policy Info", " Resource Type = "+rsrcTypeTxt.toUpperCase()));

        switch(rp.getResourceType())
        {
            case Constants.BITSTREAM:
                response = "Bitstream Name: "+Bitstream.find(
                        context, rp.getResourceID()).getName();
                break;
            case Constants.BUNDLE:
                response = "Bundle Name: "+Bundle.find(context,
                        rp.getResourceID()).getName();
                break;
        }
        System.out.println(response);
        log.debug(LogManager.getHeader(context,"Printing Policy Info", " "+response));
        response = null;

        System.out.println("Action:  "+rp.getActionText());
        log.debug(LogManager.getHeader(context,"Printing Policy Info", " Action = "+rp.getActionText()));

        if(rp.getEPerson() != null)
        {
            response = String.valueOf(rp.getEPersonID());
        }
        else
        {
            response = "null";
        }
        System.out.println("EPerson ID: "+response);
        log.debug(LogManager.getHeader(context,"Printing Policy Info", " EPerson ID = "+response));
        response = null;

        if(rp.getGroup() != null)
        {
            response = rp.getGroup().getName();
        }
        else
        {
            response = "null";
        }
        System.out.println("Group: "+response);
        log.debug(LogManager.getHeader(context,"Printing Policy Info", " Group = "+response));
        response = null;

        System.out.println("Resource Policy Name:  "+rp.getRpName());
        log.debug(LogManager.getHeader(context,"Printing Policy Info", " Resource Policy Name = "+rp.getRpName()));
        if(rp.getStartDate() != null)
        {
            DCDate date = new DCDate(rp.getStartDate());
            DateTime rpStartDate = DateTime.parse(date.toString());

            response = dft.print(rpStartDate);
        }
        else
        {
            response = "null";
        }
        System.out.println("Start Date: "+response);
        log.debug(LogManager.getHeader(context,"Printing Policy Info", " Start Date = "+response));
        response = null;

        if(rp.getEndDate() != null)
        {
            DCDate date = new DCDate(rp.getEndDate());
            DateTime rpEndDate = DateTime.parse(date.toString());

            response = dft.print(rpEndDate);
        }
        else
        {
            response = "null";
        }
        System.out.println("End Date:  "+response);
        log.debug(LogManager.getHeader(context,"Printing Policy Info", "End Date = "+response));
        response = null;

        System.out.println("Resource Policy Type:  "+rp.getRpType());
        System.out.println("Resource Policy Dscp:  "+rp.getRpDescription());
        System.out.println("-----------------------------------------------------");
        log.debug(LogManager.getHeader(context,"Printing Policy Info", " Resource Policy Type = "+rp.getRpType()));
        log.debug(LogManager.getHeader(context,"Printing Policy Info", " Resource Policy Dscp = "+rp.getRpDescription()));
        log.debug(LogManager.getHeader(context,"Printing Policy Info", "-----------------------------------------------------"));
    }

    /**
     *
     * @param context
     *      Current DSpace context.
     *
     * @param mdv
     *      Metadata value object
     *
     * @throws java.sql.SQLException
     * @throws org.dspace.authorize.AuthorizeException
     */
    public static void printMDVInfo(Context context, MetadataValue mdv)
        throws SQLException, AuthorizeException
    {
        System.out.println("Row id: "+mdv.getValueId());
        System.out.println("Item id: "+mdv.getResourceId());
        System.out.println("Field id: "+mdv.getFieldId());
        System.out.println("Value:  "+mdv.getValue());
        System.out.println("Language:  "+mdv.getLanguage());
        System.out.println("Authority: "+mdv.getAuthority());
        System.out.println("Place:  "+String.valueOf(mdv.getPlace()));
        System.out.println("Confidence:  "+String.valueOf(mdv.getConfidence()));

        System.out.println("-----------------------------------------------------");/**/

        log.debug(LogManager.getHeader(context, "Printing MetadataValue Info", "Row id = "+mdv.getValueId()));
        log.debug(LogManager.getHeader(context, "Printing MetadataValue Info", "Item id = "+mdv.getResourceId()));
        log.debug(LogManager.getHeader(context, "Printing MetadataValue Info", "Field id = "+mdv.getFieldId()));
        log.debug(LogManager.getHeader(context, "Printing MetadataValue Info", "Value = "+mdv.getValue()));
        log.debug(LogManager.getHeader(context, "Printing MetadataValue Info", "Language = "+mdv.getLanguage()));
        log.debug(LogManager.getHeader(context, "Printing MetadataValue Info", "Authority = "+mdv.getAuthority()));
        log.debug(LogManager.getHeader(context, "Printing MetadataValue Info", "Place = "+String.valueOf(mdv.getPlace())));
        log.debug(LogManager.getHeader(context, "Printing MetadataValue Info", "Confidence = "+String.valueOf(mdv.getConfidence())));

        log.debug(LogManager.getHeader(context, "", "-----------------------------------------------------"));
    }

    /**
     *
     * @param context
     * @param item
     * @throws SQLException
     * @throws IOException
     * @throws AuthorizeException
     * @throws NonUniqueMetadataException
     */
    public static void removeEmbargoEndDateMDV(Context context, Item item)
        throws SQLException, IOException, AuthorizeException
    {
        removeMetadataFieldValue(context, item, "embargo", "enddate");
    }

    /**
     * Removes the specified metadata field from the metadatavalue database table.
     *
     * @param context
     *      Current DSpace context.
     * @param item
     *      Current item being worked on.
     * @param element
     *      Element portion of a metadata field's name
     * @param qualifier
     *      Qualifier portion of a metadata field's name
     *
     * @throws java.sql.SQLException
     * @throws java.io.IOException
     * @throws org.dspace.authorizeAuthorizeException
     */
    private static void removeMetadataFieldValue(Context context, Item item, String element, String qualifier)
        throws SQLException, IOException, AuthorizeException
    {
        int mdfID = getMetadataFieldID(context, element, qualifier);

        if(getMetadataFieldValue(context, item, element, qualifier) != null)
        {
            List<MetadataValue> mdvList = MetadataValue.findByField(context, mdfID);
            if(!mdvList.isEmpty())
            {
                for(MetadataValue mdv : mdvList)
                {
                    if(mdv.getResourceId() == item.getID())
                    {
                        mdv.delete(context);
                        context.commit();
                    }
                }
            }
        }
    }

    /**
     * Determines if the specified item's resource policies
     *
     * @param contex
     *      Current DSpace context
     * @param item
     *      Current item
     * @return
     *
     * @throws org.dspace.authorize.AuthorizeException
     * @throws java.io.IOException
     * @throws java.sql.SQLException
     */
    public static boolean hasEmbargoDate(Context context, Item item)
        throws AuthorizeException, IOException, SQLException
    {
        Date embargoDate = null;
        for(ResourcePolicy itemRP : AuthorizeManager.findPoliciesByDSOAndType(context, item, ResourcePolicy.TYPE_CUSTOM))
        {
            embargoDate = itemRP.getStartDate();
        }

        return embargoDate != null;
    }

}
