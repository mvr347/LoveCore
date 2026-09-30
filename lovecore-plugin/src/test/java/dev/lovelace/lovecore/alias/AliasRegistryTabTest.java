package dev.lovelace.lovecore.alias;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AliasRegistryTabTest {

    @Test
    void textWithoutTabsIsReturnedAsIs() {
        String s = "aliases:\n  spawn:\n    target: spawn\n";
        assertSame(s, AliasRegistry.expandLeadingTabs(s));
    }

    @Test
    void leadingTabsBecomeTwoSpacesEach() {
        assertEquals("aliases:\n  spawn:\n    target: rtp\n",
                AliasRegistry.expandLeadingTabs("aliases:\n\tspawn:\n\t\ttarget: rtp\n"));
    }

    @Test
    void tabsInsideValuesAreKept() {
        assertEquals("  key: a\tb\n", AliasRegistry.expandLeadingTabs("\tkey: a\tb\n"));
    }

    @Test
    void mixedSpaceThenTabIndentIsExpanded() {
        assertEquals("    target: x\n", AliasRegistry.expandLeadingTabs("  \ttarget: x\n"));
    }

    @Test
    void windowsLineEndingsAreHandled() {
        assertEquals("a:\r\n  b: 1\r\n", AliasRegistry.expandLeadingTabs("a:\r\n\tb: 1\r\n"));
    }
}
