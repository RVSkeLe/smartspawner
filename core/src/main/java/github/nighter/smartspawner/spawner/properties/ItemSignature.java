package github.nighter.smartspawner.spawner.properties;

import lombok.Getter;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public class ItemSignature {
    private final ItemStack template;
    private final int hashCode;
    @Getter
    private final String materialName;
    @Getter
    private final int maxStackSize; // Cache purposes

    public ItemSignature(ItemStack item) {
        this.template = item.clone();
        this.template.setAmount(1);
        this.materialName = item.getType().name();
        this.hashCode = calculateHashCode();
        this.maxStackSize = item.getMaxStackSize();
    }

    // Replace the current calculateHashCode() method with:
    private int calculateHashCode() {
        // Use a faster hash algorithm and cache more item properties
        int result = 31 * template.getType().ordinal(); // Using ordinal() instead of name() hashing
        result = 31 * result + getItemDamage(template);

        // Only access ItemMeta when needed
        if (template.hasItemMeta()) {
            ItemMeta meta = template.getItemMeta();
            // Extract only the essential meta properties that determine similarity
            result = 31 * result + (meta.hasDisplayName() ? meta.displayName().hashCode() : 0);
            result = 31 * result + (meta.hasLore() ? meta.lore().hashCode() : 0);
            result = 31 * result + (meta.hasEnchants() ? meta.getEnchants().hashCode() : 0);
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemSignature that)) return false;

        // First compare cheap properties
        if (template.getType() != that.template.getType() ||
                getItemDamage(template) != getItemDamage(that.template)) {
            return false;
        }

        // Only check ItemMeta if types match
        boolean thisHasMeta = template.hasItemMeta();
        boolean thatHasMeta = that.template.hasItemMeta();

        if (thisHasMeta != thatHasMeta) {
            return false;
        }

        // If both have no meta, they're similar enough
        if (!thisHasMeta) {
            return true;
        }

        // For complex items, fall back to isSimilar but only as a last resort
        return template.isSimilar(that.template);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    public ItemStack getTemplate() {
        return template.clone();
    }

    // Non-cloning method for internal use
    public ItemStack getTemplateRef() {
        return template;
    }

    private int getItemDamage(ItemStack item) {
        if (!item.hasItemMeta()) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable) {
            return damageable.getDamage();
        }
        return 0;
    }

}
