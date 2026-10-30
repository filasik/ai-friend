package me.skerik.aifriend.gui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import me.skerik.aifriend.Context;
import me.skerik.aifriend.command.LearnedCommands;
import me.skerik.aifriend.config.Messages;
import me.skerik.aifriend.knowledge.LearnedFacts;
import me.skerik.aifriend.knowledge.LearnedFacts.Action;
import me.skerik.aifriend.knowledge.LearnedFacts.Fact;
import me.skerik.aifriend.knowledge.LearnedFacts.Status;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Review GUI for learned server facts: one page of facts of one status, tabs for the statuses, paging. Left click
 * approves, right click rejects (never learned again), shift + left disables, shift + right deletes (may be learned
 * again). Every click is cancelled by {@link LearnedGuiListener}; only these actions do anything. All texts come from the
 * messages file, fact texts only as placeholders.
 */
public final class LearnedGui implements InventoryHolder {

    static final int SIZE = 54;
    static final int PER_PAGE = 45;
    static final int PREV = 45;
    static final int NEXT = 53;
    static final int HELP = 52;
    /** Tab slots, in the order of {@link #TABS}. */
    static final int FIRST_TAB = 46;
    static final List<Status> TABS = List.of(Status.PENDING, Status.APPROVED, Status.DISABLED, Status.REJECTED, Status.CANDIDATE);
    private static final int WRAP = 40;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final Map<Status, Material> ICON = Map.of(
            Status.PENDING, Material.YELLOW_DYE, Status.APPROVED, Material.LIME_DYE, Status.DISABLED, Material.GRAY_DYE,
            Status.REJECTED, Material.RED_DYE, Status.CANDIDATE, Material.LIGHT_GRAY_DYE);

    private final Context ctx;
    private final LearnedFacts facts;
    private final Status status;
    private final int page;
    private final Inventory inventory;
    /** Fact id per slot of the current page. */
    private final int[] ids = new int[PER_PAGE];

    public LearnedGui(Context ctx, LearnedFacts facts, Status status, int page) {
        this.ctx = ctx;
        this.facts = facts;
        this.status = status;
        List<Fact> all = facts.list(status);
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        this.page = Math.clamp(page, 0, pages - 1);
        Messages m = ctx.messages();
        this.inventory = Bukkit.createInventory(this, SIZE, m.get("learned.gui.title",
                Messages.comp("status", m.get("learned.status." + LearnedCommands.statusName(status))),
                Messages.text("page", Integer.toString(this.page + 1)), Messages.text("pages", Integer.toString(pages))));
        render(all, pages);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open(Player p) {
        p.openInventory(inventory);
    }

    private void render(List<Fact> all, int pages) {
        Messages m = ctx.messages();
        java.util.Arrays.fill(ids, -1);
        List<Fact> shown = all.subList(Math.min(all.size(), page * PER_PAGE), Math.min(all.size(), (page + 1) * PER_PAGE));
        for (int i = 0; i < shown.size(); i++) {
            ids[i] = shown.get(i).id();
            inventory.setItem(i, factItem(m, shown.get(i)));
        }
        if (shown.isEmpty()) inventory.setItem(22, item(Material.BARRIER, m.get("learned.gui.empty"), List.of()));
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.space(), List.of());
        for (int s = PER_PAGE; s < SIZE; s++) inventory.setItem(s, filler);
        if (page > 0) inventory.setItem(PREV, item(Material.ARROW, m.get("learned.gui.prev"), List.of()));
        if (page < pages - 1) inventory.setItem(NEXT, item(Material.ARROW, m.get("learned.gui.next"), List.of()));
        Map<Status, Integer> counts = facts.counts();
        for (int t = 0; t < TABS.size(); t++) {
            Status s = TABS.get(t);
            ItemStack tab = item(ICON.get(s), m.get("learned.gui.tab", Messages.comp("status", m.get("learned.status." + LearnedCommands.statusName(s))),
                    Messages.text("count", Integer.toString(counts.getOrDefault(s, 0)))), m.list("learned.gui.tab-lore." + LearnedCommands.statusName(s)));
            if (s == status) tab.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            inventory.setItem(FIRST_TAB + t, tab);
        }
        inventory.setItem(HELP, item(Material.BOOK, m.get("learned.gui.help-name"), m.list("learned.gui.help")));
    }

    private ItemStack factItem(Messages m, Fact f) {
        List<Component> lore = new ArrayList<>();
        for (String line : wrap(f.text())) lore.add(m.get("learned.gui.text-line", Messages.text("text", line)));
        lore.add(Component.empty());
        lore.add(m.get("learned.gui.mentions", Messages.text("mentions", Integer.toString(f.mentions())),
                Messages.text("players", f.players().isEmpty() ? "-" : String.join(", ", f.players()))));
        lore.add(m.get("learned.gui.seen", Messages.text("first", date(f.firstSeen())), Messages.text("last", date(f.lastSeen()))));
        if (!f.decidedBy().isEmpty()) {
            lore.add(m.get("learned.gui.decided", Messages.text("by", f.decidedBy()), Messages.text("at", date(f.decidedAt()))));
        }
        lore.add(Component.empty());
        if (f.status() != Status.APPROVED) lore.add(m.get("learned.gui.hint-approve"));
        if (f.status() != Status.REJECTED) lore.add(m.get("learned.gui.hint-reject"));
        if (f.status() != Status.DISABLED) lore.add(m.get("learned.gui.hint-disable"));
        lore.add(m.get("learned.gui.hint-delete"));
        lore.add(m.get("learned.gui.hint-edit", Messages.text("id", Integer.toString(f.id()))));
        return item(ICON.get(f.status()), m.get("learned.gui.fact-name", Messages.text("id", Integer.toString(f.id()))), lore);
    }

    /** A click in the top inventory (the event is already cancelled). Runs on the player's thread. */
    void click(Player p, int slot, ClickType type) {
        if (!p.hasPermission("aifriend.teach")) {
            p.closeInventory();
            return;
        }
        if (slot == PREV) {
            reopen(p, status, page - 1);
        } else if (slot == NEXT) {
            reopen(p, status, page + 1);
        } else if (slot >= FIRST_TAB && slot < FIRST_TAB + TABS.size()) {
            reopen(p, TABS.get(slot - FIRST_TAB), 0);
        } else if (slot >= 0 && slot < PER_PAGE && ids[slot] >= 0) {
            Action action = action(type);
            if (action == null) return;
            LearnedCommands.apply(ctx, p, ids[slot], action, f -> ctx.scheduler.runForEntity(p, () -> {
                if (f == null) p.sendMessage(ctx.messages().get("learned.missing"));
                // still looking at this GUI: show the new state
                if (p.getOpenInventory().getTopInventory().getHolder(false) == this) reopen(p, status, page);
            }, null));
        }
    }

    static @Nullable Action action(ClickType type) {
        return switch (type) {
            case LEFT -> Action.APPROVE;
            case RIGHT -> Action.REJECT;
            case SHIFT_LEFT -> Action.DISABLE;
            case SHIFT_RIGHT -> Action.DELETE;
            default -> null;
        };
    }

    private void reopen(Player p, Status s, int newPage) {
        new LearnedGui(ctx, facts, s, newPage).open(p);
    }

    private static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(noItalic(name));
            if (!lore.isEmpty()) meta.lore(lore.stream().map(LearnedGui::noItalic).toList());
        });
        return stack;
    }

    /** Item texts are italic unless told otherwise. */
    private static Component noItalic(Component c) {
        return Component.empty().decoration(TextDecoration.ITALIC, false).append(c);
    }

    private static String date(long millis) {
        return millis <= 0 ? "-" : DATE.format(Instant.ofEpochMilli(millis));
    }

    /** Word wrap for lore lines; a single over-long word is cut. */
    static List<String> wrap(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            while (word.length() > WRAP) {
                if (!line.isEmpty()) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                out.add(word.substring(0, WRAP));
                word = word.substring(WRAP);
            }
            if (!line.isEmpty() && line.length() + 1 + word.length() > WRAP) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }
}
