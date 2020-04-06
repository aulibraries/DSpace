package org.dspace.xoai.filter;

import java.util.Date;

import com.lyncode.builder.DateBuilder;
import com.lyncode.xoai.dataprovider.services.api.DateProvider;
import com.lyncode.xoai.dataprovider.services.impl.BaseDateProvider;

import org.apache.solr.client.solrj.util.ClientUtils;
import org.dspace.xoai.data.DSpaceItem;
import org.dspace.xoai.filter.results.SolrFilterResult;

public class DateUntilFilter extends DSpaceFilter {

    private static final DateProvider dateProvider = new BaseDateProvider();
    private final Date date;

    public DateUntilFilter(Date date)
    {
        this.date = new DateBuilder(date).setMaxMilliseconds().build();
    }

    @Override
    public boolean isShown(DSpaceItem item)
    {
        if (item.getDatestamp().compareTo(date) <= 0)
            return true;
        return false;
    }

    @Override
    public SolrFilterResult buildSolrQuery()
    {
        String format = dateProvider.format(date).replace("Z", ".999Z"); // Tweak to set the milliseconds
        // if date has timestamp of 00:00:00, switch it to refer to end of day
        if (format.substring(11, 19).equals("00:00:00"))
        {
            format = format.substring(0, 11) + "23:59:59" + format.substring(19);
        }
        return new SolrFilterResult("dc.date.accessioned:[* TO "
                + ClientUtils.escapeQueryChars(format) + "]");
    }

}