/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.embargo;

// Java package imports
import java.sql.SQLException;
import org.apache.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Item;
import org.dspace.core.Context;


/**
 *
 * @author STONEMA
 */
public class ETDEmbargoLifter implements EmbargoLifter
{
    private static final String EMBARGOED = "EMBARGOED";
    private static final String NOT_EMBARGOED = "NOT_EMBARGOED";

    private static final Logger log = Logger.getLogger(ETDEmbargoLifter.class);

    public ETDEmbargoLifter()
    {
        super();
    }

    /**
     * Enforce lifting of embargo by turning read access to bitstreams in
     * this Item back on.
     *
     * @param context
     *      The DSpace context
     * @param item
     *      The item to embargo
     *
     * @throws java.sql.SQLException
     * @throws org.dspace.authorize.AuthorizeException
     */
    @Override
    public void liftEmbargo(Context context, Item item)
            throws SQLException, AuthorizeException
    {
        /*try
        {
            //Date now = DateTime.now().toDate();
            DateTime endDate = null;
            DateTimeFormatter dft = DateTimeFormat.forPattern("yyyy-MM-dd");
            boolean outdated = false;

            String embargoStatus = ETDEmbargoSetter.getEmbargoStatusMDV(context, item);
            String embargoLength = ETDEmbargoSetter.getEmbargoLengthMDV(context, item);
            String embargoRights = ETDEmbargoSetter.getEmbargoRightsMDV(context, item);
            String embargoEndDate = ETDEmbargoSetter.getEmbargoEndDateMDV(context, item);

            System.out.println("");
            System.out.println("Processing information for item "+item.getID());
            System.out.println("Item's Name: \""+item.getName()+"\"");
            System.out.println("Item's Handle: "+item.getHandle());
            System.out.println("Item's Archived: "+item.isArchived());
            System.out.println("Item's Withdrawn/Private:  "+item.isWithdrawn());
            System.out.println("Item's Discoverable:  "+item.isDiscoverable());
            System.out.println("-----------------------------------------------------");
            System.out.println("Item's Embargo Metadata Information");
            System.out.println("-----------------------------------------------------");
            if(embargoStatus != null)
            {
                System.out.println("Item's Embargo Status:  "+embargoStatus+"\r");
            }

            if(embargoRights != null)
            {
                System.out.println("Item's Embargo Rights:  "+embargoRights+"\r");
            }

            if(embargoLength != null)
            {
                System.out.println("Item's Embargo Length:  "+embargoLength+"\r");
            }

            if(embargoEndDate != null)
            {
                System.out.println("Item's Embargo End Date:  "+embargoEndDate);
            }

            log.debug(LogManager.getHeader(context, "", "Current Time = "+DCDate.getCurrent().displayDate(
                true, true, Locale.US)));
            log.debug(LogManager.getHeader(context, "", "Processing information for item "+item.getID()));
            log.debug(LogManager.getHeader(context, "", "Item's Name = \""+item.getName()+"\""));
            log.debug(LogManager.getHeader(context, "", "Item's Handle = "+item.getHandle()));
            log.debug(LogManager.getHeader(context, "", "Item's Archived = "+item.isArchived()));
            log.debug(LogManager.getHeader(context, "", "Item's Withdrawn/Private = "+item.isWithdrawn()));
            log.debug(LogManager.getHeader(context, "", "Item's Discoverable = "+item.isDiscoverable()));

            System.out.println("-----------------------------------------------------");
            System.out.println("Item's Current Resource Policy Information");
            System.out.println("-----------------------------------------------------");

            for(Bundle bndl : item.getBundles(Constants.CONTENT_BUNDLE_NAME))
            {
                for(ResourcePolicy bsRP : bndl.getBitstreamPolicies())
                {
                    String rpName = "";
                    if(bsRP.getRpName() != null)
                    {
                        rpName = " "+bsRP.getRpName();
                    }

                    if(bsRP.getEndDate() != null)
                    {
                        endDate = new DateTime(bsRP.getEndDate());
                    }

                    System.out.println("Original"+rpName+" Resource Policy Information:");
                    System.out.println("-----------------------------------------------------");
                    log.debug(LogManager.getHeader(context, "", "Original"+rpName+" Resource Policy Information"));
                    log.debug(LogManager.getHeader(context, "", "-----------------------------------------------------"));
                    EmbargoManager.printRPInfo(context, bsRP);

                    if(endDate != null)
                    {
                        System.out.println("Checking Policy's End Date");
                        System.out.println("-----------------------------------------------------");
                        System.out.println("Resource Policy's End Date:  "+embargoEndDate);
                        if(endDate.isBeforeNow())
                        {
                            System.out.println("Embargo End Date "+dft.print(endDate)+" has passed.");
                            outdated = true;
                        }
                        else
                        {
                            System.out.println("Embargo End Date "+dft.print(endDate)+" has NOT passed.");
                        }
                        System.out.println("-----------------------------------------------------");
                    }

                    if(outdated)
                    {
                        System.out.println("Updating Item's Resource Policy Information");
                        System.out.println("-----------------------------------------------------");
                        bsRP.setAction(Constants.READ);
                        bsRP.setRpType(ResourcePolicy.TYPE_INHERITED);
                        bsRP.setStartDate(null);
                        bsRP.setEndDate(null);
                        bsRP.update();

                        item.updateLastModified();

                        System.out.println("Updated"+rpName+" Resource Policy Information:");
                        System.out.println("-----------------------------------------------------");
                        log.debug(LogManager.getHeader(context, "", "Updated"+rpName+"Resource Policy Information"));
                        log.debug(LogManager.getHeader(context, "", "-----------------------------------------------------"));
                        EmbargoManager.printRPInfo(context, bsRP);
                    }
                }
            }
        }
        catch(SQLException | AuthorizeException | IOException exp)
        {
            // throw something here
        }*/
    }
}
