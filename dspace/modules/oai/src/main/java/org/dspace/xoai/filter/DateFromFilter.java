package org.dspace.xoai.filter;

import java.util.Date;

import com.lyncode.builder.DateBuilder;
import com.lyncode.xoai.dataprovider.services.api.DateProvider;
import com.lyncode.xoai.dataprovider.services.impl.BaseDateProvider;

import org.apache.solr.client.solrj.util.ClientUtils;
import org.dspace.xoai.data.DSpaceItem;
import org.dspace.xoai.filter.results.SolrFilterResult;

public class DateFromFilter extends DSpaceFilter {

    private static final DateProvider dateProvider = new BaseDateProvider();
    private final Date date;

    public DateFromFilter(Date date)
    {
        this.date = new DateBuilder(date).setMinMilliseconds().build();
    }

    @Override
    public boolean isShown(DSpaceItem item)
    {
        if (item.getDatestamp().compareTo(date) >= 0)
            return true;
        return false;
    }

    @Override
    public SolrFilterResult buildSolrQuery()
    {
        String format = dateProvider.format(date).replace("Z", ".000Z"); // Tweak to set the milliseconds
        return new SolrFilterResult("dc.date.accessioned:["
                + ClientUtils.escapeQueryChars(format)
                + " TO *]");
    }

}