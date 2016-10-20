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
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Item;
import org.dspace.core.Context;

/**
 * Default plugin implementation of the embargo lifting function.
 *
 * @author Larry Stone
 * @author Richard Rodgers
 * @author Michael Stone
 *
 */
public class AUroraEmbargoLifter implements EmbargoLifter
{
    public AUroraEmbargoLifter()
    {
        super();
    }

    /**
     * Enforce lifting of embargo by allow the Item to be found
     * in searches.
     *
     * @param context the DSpace context
     * @param item    the item to embargo
     * @throws java.sql.SQLException
     * @throws org.dspace.authorize.AuthorizeException
     * @throws java.io.IOException
     */
    @Override
    public void liftEmbargo(Context context, Item item)
        throws SQLException, AuthorizeException, IOException
    {
        // Once an item's embargo lift date has passed then
        // set the item's discoverable flag to true. Changing
        // this setting will allow the item to be to the public.
        item.setDiscoverable(true);
        item.update();
        item.updateLastModified();
    }
}
