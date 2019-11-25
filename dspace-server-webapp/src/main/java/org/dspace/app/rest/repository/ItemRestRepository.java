/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.dspace.app.rest.converter.MetadataConverter;
import org.dspace.app.rest.exception.DSpaceBadRequestException;
import org.dspace.app.rest.exception.RepositoryMethodNotImplementedException;
import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.app.rest.model.BundleRest;
import org.dspace.app.rest.model.ItemRest;
import org.dspace.app.rest.model.patch.Patch;
import org.dspace.app.rest.projection.Projection;
import org.dspace.app.rest.repository.patch.ItemPatch;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.service.BundleService;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.InstallItemService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.core.Context;
import org.dspace.util.UUIDUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

/**
 * This is the repository responsible to manage Item Rest object
 *
 * @author Andrea Bollini (andrea.bollini at 4science.it)
 */

@Component(ItemRest.CATEGORY + "." + ItemRest.NAME)
public class ItemRestRepository extends DSpaceObjectRestRepository<Item, ItemRest> {

    private static final Logger log = Logger.getLogger(ItemRestRepository.class);

    private final ItemService is;

    @Autowired
    MetadataConverter metadataConverter;

    @Autowired
    ItemPatch itemPatch;

    @Autowired
    BundleService bundleService;

    @Autowired
    WorkspaceItemService workspaceItemService;

    @Autowired
    ItemService itemService;

    @Autowired
    CollectionService collectionService;

    @Autowired
    InstallItemService installItemService;

    public ItemRestRepository(ItemService dsoService, ItemPatch dsoPatch) {
        super(dsoService, dsoPatch);
        this.is = dsoService;
    }

    @Override
    @PreAuthorize("hasPermission(#id, 'ITEM', 'READ')")
    public ItemRest findOne(Context context, UUID id) {
        Item item = null;
        try {
            item = is.find(context, id);
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        if (item == null) {
            return null;
        }
        return converter.toRest(item, utils.obtainProjection());
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    public Page<ItemRest> findAll(Context context, Pageable pageable) {
        try {
            long total = is.countTotal(context);
            Iterator<Item> it = is.findAll(context, pageable.getPageSize(), pageable.getOffset());
            List<Item> items = new ArrayList<>();
            while (it.hasNext()) {
                items.add(it.next());
            }
            return converter.toRestPage(items, pageable, total, utils.obtainProjection(true));
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    @PreAuthorize("hasPermission(#id, 'ITEM', #patch)")
    protected void patch(Context context, HttpServletRequest request, String apiCategory, String model, UUID id,
                         Patch patch) throws AuthorizeException, SQLException {
        patchDSpaceObject(apiCategory, model, id, patch);
    }

    @Override
    protected void updateDSpaceObject(Item item, ItemRest itemRest)
            throws AuthorizeException, SQLException {
        super.updateDSpaceObject(item, itemRest);

        Context context = obtainContext();
        if (itemRest.getWithdrawn() != item.isWithdrawn()) {
            if (itemRest.getWithdrawn()) {
                is.withdraw(context, item);
            } else {
                is.reinstate(context, item);
            }
        }
        if (itemRest.getDiscoverable() != item.isDiscoverable()) {
            item.setDiscoverable(itemRest.getDiscoverable());
            is.update(context, item);
        }
    }

    @Override
    public Class<ItemRest> getDomainClass() {
        return ItemRest.class;
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    protected void delete(Context context, UUID id) throws AuthorizeException {
        Item item = null;
        try {
            item = is.find(context, id);
            if (item == null) {
                throw new ResourceNotFoundException(ItemRest.CATEGORY + "." + ItemRest.NAME +
                                                            " with id: " + id + " not found");
            }
            if (is.isInProgressSubmission(context, item)) {
                throw new UnprocessableEntityException("The item cannot be deleted. "
                                                               + "It's part of a in-progress submission.");
            }
            if (item.getTemplateItemOf() != null) {
                throw new UnprocessableEntityException("The item cannot be deleted. "
                                                               + "It's a template for a collection");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        try {
            is.delete(context, item);
        } catch (SQLException | IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    protected ItemRest createAndReturn(Context context) throws AuthorizeException, SQLException {
        HttpServletRequest req = getRequestService().getCurrentRequest().getHttpServletRequest();
        String owningCollectionUuidString = req.getParameter("owningCollection");
        ObjectMapper mapper = new ObjectMapper();
        ItemRest itemRest = null;
        try {
            ServletInputStream input = req.getInputStream();
            itemRest = mapper.readValue(input, ItemRest.class);
        } catch (IOException e1) {
            throw new UnprocessableEntityException("Error parsing request body", e1);
        }

        if (itemRest.getInArchive() == false) {
            throw new DSpaceBadRequestException("InArchive attribute should not be set to false for the create");
        }
        UUID owningCollectionUuid = UUIDUtils.fromString(owningCollectionUuidString);
        Collection collection = collectionService.find(context, owningCollectionUuid);
        if (collection == null) {
            throw new DSpaceBadRequestException("The given owningCollection parameter is invalid: "
                                                        + owningCollectionUuid);
        }
        WorkspaceItem workspaceItem = workspaceItemService.create(context, collection, false);
        Item item = workspaceItem.getItem();
        item.setArchived(true);
        item.setOwningCollection(collection);
        item.setDiscoverable(itemRest.getDiscoverable());
        item.setLastModified(itemRest.getLastModified());
        metadataConverter.setMetadata(context, item, itemRest.getMetadata());

        Item itemToReturn = installItemService.installItem(context, workspaceItem);

        return converter.toRest(itemToReturn, Projection.DEFAULT);
    }

    @Override
    @PreAuthorize("hasPermission(#uuid, 'ITEM', 'WRITE')")
    protected ItemRest put(Context context, HttpServletRequest request, String apiCategory, String model, UUID uuid,
                           JsonNode jsonNode)
            throws RepositoryMethodNotImplementedException, SQLException, AuthorizeException {
        HttpServletRequest req = getRequestService().getCurrentRequest().getHttpServletRequest();
        ObjectMapper mapper = new ObjectMapper();
        ItemRest itemRest = null;
        try {
            itemRest = mapper.readValue(jsonNode.toString(), ItemRest.class);
        } catch (IOException e1) {
            throw new UnprocessableEntityException("Error parsing request body", e1);
        }

        Item item = itemService.find(context, uuid);
        if (item == null) {
            throw new ResourceNotFoundException(apiCategory + "." + model + " with id: " + uuid + " not found");
        }

        if (StringUtils.equals(uuid.toString(), itemRest.getId())) {
            metadataConverter.setMetadata(context, item, itemRest.getMetadata());
        } else {
            throw new IllegalArgumentException("The UUID in the Json and the UUID in the url do not match: "
                                                       + uuid + ", "
                                                       + itemRest.getId());
        }
        return converter.toRest(item, Projection.DEFAULT);
    }

    /**
     * Method to add a bundle to an item
     *
     * @param context    The context
     * @param item       The item to which the bundle has to be added
     * @param bundleRest The bundleRest that needs to be added to the item
     * @return The added bundle
     */
    public Bundle addBundleToItem(Context context, Item item, BundleRest bundleRest)
            throws SQLException, AuthorizeException {
        if (item.getBundles(bundleRest.getName()).size() > 0) {
            throw new DSpaceBadRequestException("The bundle name already exists in the item");
        }

        Bundle bundle = bundleService.create(context, item, bundleRest.getName());

        metadataConverter.setMetadata(context, bundle, bundleRest.getMetadata());
        bundle.setName(context, bundleRest.getName());

        context.commit();

        return bundle;

    }
}