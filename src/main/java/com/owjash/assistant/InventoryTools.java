package com.owjash.assistant;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import java.util.*;

// A new instance is created for each request; evidence is never shared between users or requests.
public class InventoryTools {
    public record Evidence(String tool,Object result) {}
    private final InventoryService inventory;
    private final List<Evidence> evidence=Collections.synchronizedList(new ArrayList<>());
    InventoryTools(InventoryService inventory) { this.inventory=inventory; }
    private Object record(String name,Object result) {
        if (evidence.size()>=8) throw new IllegalStateException("Tool limit reached. Answer using the data already returned.");
        evidence.add(new Evidence(name,result)); return result;
    }
    @Tool(description="Get current inventory totals: product count, stock units, low-stock count, suppliers, and inventory value in USD.")
    public Object getInventorySummary() { return record("getInventorySummary",inventory.dashboard()); }
    @Tool(description="Get current products whose quantity is at or below their reorder threshold, including their suppliers. At most 50 products are returned.")
    public Object getLowStockProducts() { return record("getLowStockProducts",inventory.lowStock().stream().limit(50).toList()); }
    @Tool(description="Search current inventory by product name, SKU, category, or supplier. Returns at most 50 matching records.")
    public Object searchProducts(@ToolParam(description="Search text; an empty string lists up to 50 products") String query) {
        return record("searchProducts",inventory.products(query==null?"":query).stream().limit(50).toList());
    }
    @Tool(description="Get recorded sales revenue, order count, daily totals and top-selling products for a number of calendar days including today (UTC). Excludes receipts. No forecasting.")
    public Object getSalesSummary(@ToolParam(description="Number of days, between 1 and 366") int days) {
        return record("getSalesSummary",inventory.sales(days));
    }
    public List<Evidence> evidence() { return List.copyOf(evidence); }
}
