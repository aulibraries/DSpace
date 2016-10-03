/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.embargo;

// Java class imports
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
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.DCDate;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.ItemIterator;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataSchema;
import org.dspace.content.MetadataValue;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.core.PluginManager;
import org.dspace.handle.HandleManager;
import org.dspace.workflow.WorkflowItem;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;


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
    private static final Logger log = Logger.getLogger(EmbargoManager.class);

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

    // Custom constants
    private static String lengthTermMDF = null;
    private static String liftDateMDF = null;
    private static String openTermsMDF = null;

    private static final String defaultEmbargoVal = "SCHEMA.ELEMENT.QUALIFIER";

    /**
     * Put an Item under embargo until the specified lift date.
     * Calls EmbargoSetter plugin to adjust Item access control policies.
     *
     * @param context the DSpace context
     * @param item the item to embargo
     * @throws java.sql.SQLException
     * @throws org.dspace.authorize.AuthorizeException
     * @throws java.io.IOException
     */
    public static void setEmbargo(Context context, Item item)
        throws SQLException, AuthorizeException, IOException
    {
        init();

        try
        {
            context.turnOffAuthorisationSystem();
            //log.debug(LogManager.getHeader(context, "Executing Setter Method", " setEmbargo"));
            setter.setEmbargo(context, item);
        }
        finally
        {
            if(context.ignoreAuthorization())
            {
                context.restoreAuthSystemState();
            }
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
     * @throws java.sql.SQLException
     * @throws org.dspace.authorize.AuthorizeException
     * @throws java.io.IOException
     */
    public static DCDate getEmbargoTermsAsDate(Context context, Item item)
        throws SQLException, AuthorizeException, IOException
    {
        /**
         * This code is commented out because the functionality this method provided is
         * no longer relevant to us. It's code that was a part of the pre-3.0 version of
         * DSpace embargo system. The AUETD system's embargo now follows the new embargo
         * methodology of creating resource policies for each user group account authorized
         * to view the associated item and assets.
         *
         * The function will now simply return null;
         */

        init();
        /*DCDate result = null;

        if(item.isArchived() && WorkflowItem.findByItem(context, item) == null)
        {
            String term = null;
            //List<Metadatum> termsList = item.getMetadata("dc", "embargo", "length", Item.ANY, Item.ANY);
            //List<MetadataValue> termsList = MetadataValue.findByField(context, DCEmbargoLengthMDFID);
            Metadatum[] termsMDV = item.getMetadata(MetadataSchema.DC_SCHEMA, "embargo", "length", Item.ANY);

            if(termsMDV.length > 0)
            {
                term = termsMDV[0].value;
            }
            else
            {
                return null;
            }

            if(term == null)
            {
                return null;
            }

            result = setter.parseTerms(context, item, term);
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
     * @throws java.sql.SQLException
     * @throws org.dspace.authorize.AuthorizeException
     * @throws java.io.IOException
     */
    public static void liftEmbargo(Context context, Item item)
        throws SQLException, AuthorizeException, IOException
    {
        init();

        /**
         * Altering the value of an item's date avaliable meta data field is not
         * exactly the desired action to perform in order to lift an item's
         * embargo.  The desired action is to alter the resource policies of the
         * item and its associated bundles and bit streams.
         */
        lifter.liftEmbargo(context, item);

        log.info("Lifting embargo on Item "+item.getHandle());
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
            //context.setIgnoreAuthorization(true);
            context.turnOffAuthorisationSystem();

            DateTime now = DateTime.now();

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
                        if (processOneItem(context, (Item)dso, line, now.toDate()))
                        {
                            status = 1;
                        }
                    }
                }
            }
            else
            {
                if(log.isDebugEnabled())
                {
                    System.out.println("-----------------------------------------------------");
                    System.out.println("\n");
                    System.out.println("Processing all item's in the database.");
                }
                //ItemIterator ii = Item.findByMetadataField(context, lift_schema, lift_element, lift_qualifier, Item.ANY);

                if(line.hasOption('a') || line.hasOption('c'))
                {
                    ItemIterator ii = Item.findAll(context);
                    List<Item> itemArrayList = new ArrayList<Item>();
                    while (ii.hasNext())
                    {
                        Item nextItem = ii.next();

                        itemArrayList.add(nextItem);
                    }

                    if(!itemArrayList.isEmpty())
                    {
                        Collections.sort(itemArrayList, new Comparator<Item>()
                        {
                            @Override
                            public int compare(Item item1, Item item2)
                            {
                                int item1ID = item1.getID();
                                int item2ID = item2.getID();

                                return (item1ID < item2ID ? -1 : (item1ID == item2ID ? 0 : 1));
                            }
                        });

                        for(Item item : itemArrayList)
                        {
                            if(processOneItem(context, item, line, now.toDate()))
                            {
                                status = 1;
                            }
                        }
                    }
                }

                if(line.hasOption('l') || line.hasOption('c'))
                {
                    ItemIterator ii = Item.findAllUnfiltered(context);
                    List<Item> culledItemList = new ArrayList<Item>();

                    while (ii.hasNext())
                    {
                        Item nextItem = ii.next();

                        if(nextItem.isArchived() || nextItem.isWithdrawn())
                        {
                            List<ResourcePolicy> bsRPList = null;
                            for(Bundle bndl : nextItem.getBundles(Constants.DEFAULT_BUNDLE_NAME))
                            {
                                bsRPList = bndl.getBitstreamPolicies();
                            }

                            if(bsRPList != null)
                            {
                                for(ResourcePolicy bsRP : bsRPList)
                                {
                                    if(bsRP.getStartDate() != null || bsRP.getEndDate() != null)
                                    {
                                        // assigns the month of the current item's resource policy ending date as an integer value.
                                        int rpEndDateMonth = new DateTime(bsRP.getEndDate()).getMonthOfYear();

                                        // compare the resource policy's ending date month with the current month
                                        // if the equal each other then proceed.
                                        if(rpEndDateMonth == now.getMonthOfYear())
                                        {
                                            if(!culledItemList.contains(nextItem))
                                            {
                                                culledItemList.add(nextItem);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if(!culledItemList.isEmpty())
                    {
                        if(log.isDebugEnabled())
                        {
                            System.out.println("Number of Items to Process: "+String.valueOf(culledItemList.size()));
                        }

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
                            if(processOneItem(context, culledItem, line, now.toDate()))
                            {
                                status = 1;
                            }
                        }
                    }
                }

                if(line.hasOption('a'))
                {
                    WorkflowItem[] wfiList = WorkflowItem.findAll(context);

                    if(wfiList != null )
                    {
                        for(WorkflowItem wfi : wfiList)
                        {
                            Item nextItem = wfi.getItem();

                            if(processOneItem(context, nextItem, line, now.toDate()))
                            {
                                status = 1;
                            }
                        }
                    }
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

    /**
     * Lift or check embargo on one Item, handle exceptions
     * 
     * @param context
     *          Current context
     * @param item
     *          Item being worked on.
     * @param line
     *          CommandLine object
     * @param now
     *          Current date.
     * @return
     *      Return false on success, true if there was a fatal exception.
     * 
     * @throws Exception 
     */
    private static boolean processOneItem(Context context, Item item, CommandLine line, Date now)
        throws Exception
    {
        boolean status = false;
        //Metadatum lift[] = item.getMetadata(lift_schema, lift_element, lift_qualifier, Item.ANY);

        if(line.hasOption('x'))
        {
            List<ResourcePolicy> itemRPList = AuthorizeManager.getPolicies(context, item);

            if(itemRPList != null)
            {
                for(ResourcePolicy itemRP : itemRPList)
                {
                    if(ResourcePolicy.TYPE_SUBMISSION.equals(itemRP.getRpType()) || ResourcePolicy.TYPE_WORKFLOW.equals(itemRP.getRpType()))
                    {
                        return status;
                    }
                }
            }
        }

        if(log.isDebugEnabled())
        {
            String embargoStatus = getEmbargoStatusMDV(context, item);
            String embargoLength = getEmbargoLengthMDV(context, item);
            String embargoRights = getEmbargoRightsMDV(context, item);

            System.out.println("\n");
            System.out.println("Processing information for item "+item.getID());
            System.out.println("Item's Name: \""+item.getName()+"\"");
            System.out.println("Item's Handle: "+item.getHandle());
            System.out.println("Item's Archived: "+item.isArchived());
            System.out.println("Item's Withdrawn/Private:  "+item.isWithdrawn());
            System.out.println("Item's Discoverable:  "+item.isDiscoverable());

            System.out.println("-----------------------------------------------------");
            System.out.println("METADATA INFORMATION");
            System.out.println("-----------------------------------------------------");
            if(embargoStatus != null)
            {
                System.out.println("Item's Embargo Status:  "+embargoStatus);
            }

            if(embargoRights != null)
            {
                System.out.println("Item's Embargo Rights:  "+embargoRights);
            }

            if(embargoLength != null)
            {
                System.out.println("Item's Embargo Length:  "+embargoLength);
            }
            System.out.println("-----------------------------------------------------");

            System.out.println("");
            System.out.println("BITSTREAM RESOURCE POLICY INFORMATION");
            System.out.println("-----------------------------------------------------");

            for(Bundle bndl : item.getBundles(Constants.DEFAULT_BUNDLE_NAME))
            {
                for(ResourcePolicy bsRP : bndl.getBitstreamPolicies())
                {
                    String policyName = "Public_Read";
                    if(bsRP.getRpName() != null)
                    {
                        policyName = bsRP.getRpName();
                    }

                    System.out.println(policyName+" Bitstream resource policy information:");
                    System.out.println("-----------------------------------------------------");
                    printRPInfo(context, bsRP);
                    System.out.println("-----------------------------------------------------");
                }
            }
            System.out.println("");
            System.out.println("=====================================================");
        }

        // need to survive any failure on a single item, go on to process the rest.
        try
        {
            if (line.hasOption('a'))
            {
                /*if(line.hasOption('v'))
                {
                    ETDEmbargoSetter.convertEmbargoSettings(context, item, true);
                }
                else
                {
                    ETDEmbargoSetter.convertEmbargoSettings(context, item, false);
                }

                log.debug(LogManager.getHeader(context, "Conversion Complete", "******************* Conversion of Item "+item.getHandle()+" Complete ************************* "));*/
            }
            else
            {
                 /*List<MetadataValue> dateAvailableList = MetadataValue.findByField(
        context, 12);

                DCDate dateAvailable = new DCDate(now);

                if(!dateAvailableList.isEmpty())
                {
                    for(MetadataValue dateAvailMDV : dateAvailableList)
                    {
                        if(dateAvailMDV.getItemId() == item.getID())
                        {
                            if(!dateAvailMDV.getValue().startsWith("MONTH") || !dateAvailMDV.getValue().startsWith("NO"))
                            {
                                dateAvailable = new DCDate(dateAvailMDV.getValue());
                            }
                        }
                    }
                }*/

                if (line.hasOption('v'))
                {
                    //System.out.println("Lifting embargo from Item handle=" + item.getHandle() + ", lift date=" + liftDate.toString());
                    //System.out.println("Lifting embargo from Item handle=" + item.getHandle() + ", lift date=" + dateAvailable.toString());
                }
                if (line.hasOption('n'))
                {
                    if(log.isDebugEnabled())
                    {
                        System.out.println("Checking Embargo");
                        System.out.println("-----------------------------------------------------");
                    }

                    if (!line.hasOption('q'))
                    {
                        //System.out.println("DRY RUN: would have lifted embargo from Item handle=" + item.getHandle() + ", lift date=" +liftDate.toString());
                        //System.out.println("DRY RUN: would have lifted embargo from Item handle=" + item.getHandle() + ", lift date=" +dateAvailable.toString());
                    }

                    log.info(LogManager.getHeader(context, "Checking Embargo", "******************* Checking Embargo Settings of Item "+item.getHandle()+" ************************* "));

                    setter.checkEmbargo(context, item);

                    if(log.isDebugEnabled())
                    {
                        System.out.println("-----------------------------------------------------");
                        System.out.println("");
                        System.out.println("=====================================================");
                    }
                }
                else if (!line.hasOption('c'))
                {
                    /*System.out.println("Lifting Embargo");
                    System.out.println("-----------------------------------------------------");*/

                    log.info(LogManager.getHeader(context, "Lifting Embargo", "******************* Lifting Embargo on Item "+item.getHandle()+" ************************* "));
                    liftEmbargo(context, item);

                    if(log.isDebugEnabled())
                    {
                        System.out.println("-----------------------------------------------------");
                        System.out.println("");
                        System.out.println("=====================================================");
                    }
                }
                else if (!line.hasOption('l'))
                {
                    if(log.isDebugEnabled())
                    {
                        System.out.println("Checking Embargo");
                        System.out.println("-----------------------------------------------------");
                    }

                    if (line.hasOption('v'))
                    {
                        //System.out.println("Checking current embargo on Item handle=" + item.getHandle() + ", lift date=" + liftDate.toString());
                        //System.out.println("Checking current embargo on Item handle=" + item.getHandle() + ", lift date=" + dateAvailable.toString());
                    }

                    log.info(LogManager.getHeader(context, "Checking Embargo", "******************* Checking Embargo Settings of Item "+item.getHandle()+" ************************* "));
                    setter.checkEmbargo(context, item);

                    if(log.isDebugEnabled())
                    {
                        System.out.println("-----------------------------------------------------");
                        System.out.println("");
                        System.out.println("=====================================================");
                    }
                }
            }
        }
        catch (SQLException e)
        {
            log.error("Failed attempting to lift embargo, item="+item.getHandle()+": ", e);
            System.err.println("Failed attempting to lift embargo, item="+item.getHandle()+": "+ e);
            e.printStackTrace();
            status = true;
        } catch (AuthorizeException e)
        {
            log.error("Failed attempting to lift embargo, item="+item.getHandle()+": ", e);
            System.err.println("Failed attempting to lift embargo, item="+item.getHandle()+": "+ e);
            e.printStackTrace();
            status = true;
        } catch (IOException e)
        {
            log.error("Failed attempting to lift embargo, item="+item.getHandle()+": ", e);
            System.err.println("Failed attempting to lift embargo, item="+item.getHandle()+": "+ e);
            e.printStackTrace();
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
            lengthTermMDF = ConfigurationManager.getProperty("embargo.field.terms");
            liftDateMDF = ConfigurationManager.getProperty("embargo.field.lift");
            openTermsMDF = ConfigurationManager.getProperty("embargo.terms.open");

            if (lengthTermMDF  == null || liftDateMDF == null)
            {
                throw new IllegalStateException("Missing one or more of the required DSpace configuration properties for EmbargoManager, check your configuration file.");
            }

            if(!lengthTermMDF.equals(defaultEmbargoVal))
            {
                terms_schema = getSchemaOf(lengthTermMDF);
                terms_element = getElementOf(lengthTermMDF);
                terms_qualifier = getQualifierOf(lengthTermMDF);
            }

            if(!liftDateMDF.equals(defaultEmbargoVal))
            {
                lift_schema = getSchemaOf(liftDateMDF);
                lift_element = getElementOf(liftDateMDF);
                lift_qualifier = getQualifierOf(liftDateMDF);
            }

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
    /**
     * Return the schema part of "schema.element.qualifier" metadata field spec
     * 
     * @param field
     *      Name of metadata field.
     * @return 
     *       Return the schema part of "schema.element.qualifier" metadata field spec
     */
    private static String getSchemaOf(String field)
    {
        String sa[] = field.split("\\.", 3);
        return sa[0];
    }

    
    /**
     * Return the element part of "schema.element.qualifier" metadata field spec, if any
     * 
     * @param field
     *      Name of metadata field
     * @return 
     *      Return the element part of "schema.element.qualifier" 
     *      metadata field spec
     */
    private static String getElementOf(String field)
    {
        String sa[] = field.split("\\.", 3);
        return sa.length > 1 ? sa[1] : null;
    }

    /**
     * Return the qualifier part of "schema.element.qualifier" metadata field spec, if any
     * 
     * @param field
     *      Name of metadata field
     * @return 
     *      Returns the qualifier part of the "schema.element.qualifier"
     *      metadata field spec.
     */
    private static String getQualifierOf(String field)
    {
        String sa[] = field.split("\\.", 3);
        return sa.length > 2 ? sa[2] : null;
    }

    /**
     * Return the lift date assigned when embargo was set, or null, if either:
     * it was never under embargo, or the lift date has passed.
     * 
     * @param item
     *      Item being worked on.
     * @return 
     */
    private static DCDate recoverEmbargoDate(Item item)
    {
        /*DCDate liftDate = null;
        DCValue lift[] = item.getMetadata(lift_schema, lift_element, lift_qualifier, Item.ANY);

        if (lift.length > 0)
        {
            liftDate = new DCDate(lift[0].value);
            // sanity check: do not allow an embargo lift date in the past.
            if (liftDate.toDate().before(new Date()))
            {
                liftDate = null;
            }
        }
        return liftDate;*/

        return null;
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
     * Returns an item's embargo rights metadata info
     *
     * @param context
     *      Context
     * @param item
     *      Item being worked on
     * @return
     *
     * @throws java.io.IOException
     * @throws java.sql.SQLException
     * @throws org.dspace.authorize.AuthorizeException
     */
    public static String getEmbargoRightsMDV(Context context, Item item)
        throws IOException, SQLException, AuthorizeException
    {
        return getMetadataFieldValue(context, item, "rights", null);
    }

    /**
     * Returns an item's embargo length metadata info
     *
     * @param context
     *      Current Dspace context
     * @param item
     *      Current item being worked on.
     *
     * @return
     *      Returns a string representation of an item's dc.embargo.length
     *      metadata field value.
     *
     * @throws java.io.IOException
     * @throws java.sql.SQLException
     * @throws org.dspace.authorize.AuthorizeException
     */
    public static String getEmbargoLengthMDV(Context context, Item item)
        throws IOException, SQLException, AuthorizeException
    {
        return getMetadataFieldValue(context, item, "embargo", "length");
    }

    /**
     * Returns an item's embargo end date metadata info
     *
     * @param context
     *      Context
     * @param item
     *      Item being worked on
     * @return
     *
     * @throws java.io.IOException
     * @throws java.sql.SQLException
     * @throws org.dspace.authorize.AuthorizeException
     */
    public static String getEmbargoEndDateMDV(Context context, Item item)
        throws IOException, SQLException, AuthorizeException
    {
        return getMetadataFieldValue(context, item, "embargo", "enddate");
    }

    /**
     * Returns an item's embargo status metadata info
     *
     * @param context
     *      Current Dspace context
     * @param item
     *      Current item being worked on.
     *
     * @return
     *      Returns a string representation of an item's dc.embargo.length
     *      metadata field value.
     *
     * @throws java.io.IOException
     * @throws java.sql.SQLException
     * @throws org.dspace.authorize.AuthorizeException
     */
    public static String getEmbargoStatusMDV(Context context, Item item)
        throws IOException, SQLException, AuthorizeException
    {
        return getMetadataFieldValue(context, item, "embargo", "status");
    }

    /**
     * Returns an item's dc.date.accessioned metadata value.
     * 
     * @param context
     *          Current context
     * @param item
     *          Current item being worked on.
     * @return
     *          Return an item's dc.date.accessioned metadata value
     * 
     * @throws AuthorizeException
     * @throws IOException
     * @throws SQLException
     */
    public static String getDateAccessionedMDV(Context context, Item item)
        throws AuthorizeException, IOException, SQLException
    {
        return getMetadataFieldValue(context, item, "date", "accessioned");
    }

    /**
     * Remove an item's embargo/resource policies.
     * 
     * @param context
     *          Current context
     * @param item
     *          Current item being worked on
     * 
     * @throws SQLException
     * @throws IOException
     * @throws AuthorizeException
     */
    public static void removeETDEmbargoPolicies(Context context, Item item)
        throws SQLException, IOException, AuthorizeException
    {
        Bundle[] bndls = item.getBundles(Constants.CONTENT_BUNDLE_NAME);

        if(bndls.length > 0)
        {
            Bundle bndl = bndls[0];

            List<ResourcePolicy> bsRPList = bndl.getBitstreamPolicies();
            if(bsRPList != null)
            {
                for(ResourcePolicy bsRP : bsRPList)
                {
                    // Only perform these actions if the
                    // embargo policy's end date value is set.
                    if(bsRP.getEndDate() != null)
                    {
                        bsRP.delete();
                    }
                }
            }
        }
    }
    
    /**
     * Remove an item's dc.embargo.end_date metadata value.
     * 
     * @param context
     *          Current context
     * @param item
     *          Current item being worked on
     * 
     * @throws SQLException
     * @throws IOException
     * @throws AuthorizeException 
     */
    public static void removeEmbargoEndDateMDV(Context context, Item item)
        throws SQLException, IOException, AuthorizeException
    {
        removeMetadataFieldValue(context, item, "embargo", "enddate");
    }
    
    /**
     * Remove an item's dc.rights metadata value.
     * 
     * @param context
     *          Current context
     * @param item
     *          Current item being worked on.
     * 
     * @throws SQLException
     * @throws IOException
     * @throws AuthorizeException 
     */
    public static void removeEmbargoRightsMDV(Context context, Item item)
        throws SQLException, IOException, AuthorizeException
    {
        removeMetadataFieldValue(context, item, "rights", null);
    }
    
    /**
     * Remove an item's dc.embargo.length metadata value.
     * 
     * @param context
     *          Current context
     * @param item
     *          Current item being worked on.
     * 
     * @throws SQLException
     * @throws IOException
     * @throws AuthorizeException 
     */
    public static void removeEmbargoLengthMDV(Context context, Item item)
        throws SQLException, IOException, AuthorizeException
    {
        removeMetadataFieldValue(context, item, "embargo", "length");
    }

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
     * Create a new or modify an existing metadata value.
     * 
     * @param context
     *          Current context
     * @param item
     *          Current item being worked on.
     * @param element
     *          Element portion of a metadata value name.
     * @param qualifier
     *          Qualifier portion of a metadata value name.
     * @param value
     *          Content that will occupy the metadata value record.
     * 
     * @throws SQLException
     * @throws IOException
     * @throws AuthorizeException
     */
    public static void CreateOrModifyEmbargoMetadataValue(Context context, Item item, String element, String qualifier, String value)
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
                        mdv.setValue(value);
                        mdv.setResourceTypeId(item.getType());
                        mdv.update(context);

                        context.commit();
                    }
                }
            }
        }
        else
        {
            MetadataValue mdv = new MetadataValue();

            mdv.setFieldId(mdfID);
            mdv.setResourceId(item.getID());
            mdv.setResourceTypeId(item.getType());
            mdv.setValue(value);
            mdv.setLanguage("en_US");
            mdv.setPlace(1);
            mdv.setAuthority(null);
            mdv.setConfidence(-1);
            mdv.create(context);

            context.commit();
        }
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
     * Output information about a submitted embargo lift date.
     * 
     * @param context
     *      Current DSpace context
     * @param liftDate
     *      End/lift date that will be assigned to a resource policy.
     */
    protected static void printLiftDateInfo(Context context, DCDate liftDate)
    {
        if(liftDate != null)
        {
            System.out.println("New Lift Date:  "+liftDate.toString());
            DateTime tempDate = new DateTime(liftDate.toDate());
            //DateTimeFormatter dft = DateTimeFormat.forPattern(termsOpen);
            System.out.println("New Lift Date in Millis:  "+tempDate.getMillis());
            System.out.println("Translates To: ");
            System.out.println("-----------------------------------------------------");
            System.out.println("DateTime Year:  "+tempDate.getYear());
            System.out.println("DCDate Year:  "+liftDate.getYear());
            System.out.println("DateTime Month:  "+tempDate.getMonthOfYear());
            System.out.println("DCDate Month:  "+liftDate.getMonth());
            System.out.println("DateTime Day:  "+tempDate.getDayOfMonth());
            System.out.println("DCDate Day:  "+liftDate.getDay());
            System.out.println("DateTime Time: "+tempDate.getHourOfDay()+":"+tempDate.getMinuteOfHour()+":"+tempDate.getSecondOfMinute());
            System.out.println("DCDate Time:  "+liftDate.getHour()+":"+liftDate.getMinute()+":"+liftDate.getSecond());
            System.out.println("Time Zone:  "+tempDate.getZone());

             log.debug(LogManager.getHeader(context, "Printing Lift Date Info", " New Lift Date = "+liftDate.toString()));
            //DateTimeFormatter dft = DateTimeFormat.forPattern(termsOpen);
            log.debug(LogManager.getHeader(context, "Printing Lift Date Info", " New Lift Date in Millis = "+tempDate.getMillis()));
            log.debug(LogManager.getHeader(context, "Printing Lift Date Info", " Translates To = "));
            log.debug(LogManager.getHeader(context, "", "-----------------------------------------------------"));
            log.debug(LogManager.getHeader(context, "Printing Lift Date Info", " DateTime Year = "+tempDate.getYear()));
            log.debug(LogManager.getHeader(context, "Printing Lift Date Info", " DCDate Year = "+liftDate.getYear()));
            log.debug(LogManager.getHeader(context, "Printing Lift Date Info", " DateTime Month = "+tempDate.getMonthOfYear()));
            log.debug(LogManager.getHeader(context, "Printing Lift Date Info", " DCDate Month = "+liftDate.getMonth()));
            log.debug(LogManager.getHeader(context, "Printing Lift Date Info", " DateTime Day = "+tempDate.getDayOfMonth()));
            log.debug(LogManager.getHeader(context, "Printing Lift Date Info", " DCDate Day = "+liftDate.getDay()));
            log.debug(LogManager.getHeader(context, "Printing Lift Date Info", " DateTime Time = "+tempDate.getHourOfDay()+":"+tempDate.getMinuteOfHour()+":"+tempDate.getSecondOfMinute()));
            log.debug(LogManager.getHeader(context, "Printing Lift Date Info", "DCDate Time = "+liftDate.getHour()+":"+liftDate.getMinute()+":"+liftDate.getSecond()));
            log.debug(LogManager.getHeader(context, "Printing Lift Date Info", "Time Zone = "+tempDate.getZone()));
        }
        else
        {
            System.out.println("Lift Date:  null");
            log.debug(LogManager.getHeader(context, "Printing Lift Date Info", " null"));
        }
    }

    /**
     * Output information about a submitted metadata value object.
     * 
     * @param context
     *      Current DSpace context.
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
}
