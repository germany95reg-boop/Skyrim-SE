package com.kebrio.skyrimcheats.overlay;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class CommandCatalog {
 private CommandCatalog(){}
 private static final String[] BASIC = {
  "#console","#enter","#backspace","tgm",
  "player.setav speedmult 150","player.setav speedmult 100",
  "player.modav carryweight 10000","player.modav carryweight -10000",
  "player.forceav shoutrecoverymult 0","player.forceav shoutrecoverymult 1",
  "player.additem 0000000F 100000",
  "player.additem 0000000A 1000",
  "player.modav dragonsouls 100",
  "unlock","psb",
  "player.setav alchemy 100",
  "player.setav smithing 100",
  "player.setav enchanting 100",
  "player.setav alteration 100",
  "player.setav conjuration 100",
  "player.setav destruction 100",
  "player.setav illusion 100",
  "player.setav restoration 100"
 };
 private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList(BASIC));
 public static boolean isAllowed(String c) { return ALLOWED.contains(c); }
}
