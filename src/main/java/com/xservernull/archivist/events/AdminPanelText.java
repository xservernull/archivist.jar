// the texts of the admin panel window (old archivistplayerdata.formatdimensionlog): for every
// dimension a log line (what the administrator wrote there) and a hint (how to reach the next

package com.xservernull.archivist.events;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class AdminPanelText {
    private AdminPanelText() {}

    private record Entry(String log, String hint) {}

    private static final Map<String, Entry> ENTRIES = new HashMap<>();

    static {
        put("childhoodhome",
                "hes been waiting for so long.",
                "i hate iron golems. if u find any in the overworld please just destroy it for me.");
        put("library",
                "around all these books... something is hiding in the void... i never had the guts to check it thou",
                "type 'thepit' inside the adminpanel.");
        put("old_nether",
                "hit the weird spinning block. kinda straightforward.",
                "grab an explosive n water it at home. i wouldnt recommend missing the watering it part. tried it before...");
        put("xor_world",
                "XNORXNORXNOR",
                "u want a new dim so bad? fine. something is under the overworld.");
        put("m4kb_world",
                "small but important",
                "hell itself was kinder, you know how to get to hell anyways. nether, hell, whatever...");
        put("mansion_dim",
                "get upstairs at the mansion and DONT RUN BACK",
                "keep climbing. dont look behind you, dont go back down. just up.");
        put("echo_chamber",
                "RUNUPSTAIRSRUNUPSTAIRSRUNUPSTAIRSRUNUPSTAIRSRU",
                "tame any old friend, one who was calling bark by bark for some time.");
        put("far_lands",
                "you have always been connected with messy, beautiful things. all humans are.",
                "can you break the weird fake bedrock on top of the overworld? weird corruption error.");
        put("bedrock_far_lands",
                "theres a keeper here. he loves poppies.",
                "find heaven. remember the sky portal? the real one is in villllage!");
        put("villllage",
                "You found a new friend!",
                "write what you feel in a book and sign it. its worth it. just for yourself this time.");
        put("neverforgetyou",
                "Let forgiving be kinder than grief. Not a devil, just a nobody.",
                "find the gaps in your main world, jump into an odd hole.");
        put("rd132011",
                "The Final Dance.",
                "somewhere in rd, someone left a command block behind. find it.");
        put("infdev20100227",
                "not familiar enough, im guessing.",
                "something in infdev can stun. an axe, i think. its pretty helpful.");
        put("quietplace",
                "im here",
                "hes not a white eyed monster.");
        put("aether",
                "A Valley of Fools.",
                "build his shrine, the one with a white sight and a steve skin.");
    }

    private static void put(String dimension, String log, String hint) {
        ENTRIES.put(dimension, new Entry(
                "archivist.panel.log." + dimension + "|" + log,
                hint.isEmpty() ? "" : "archivist.panel.hint." + dimension + "|" + hint));
    }

    static List<String> logs(List<String> dimensions) {
        return dimensions.stream().map(ENTRIES::get).filter(e -> e != null).map(Entry::log).toList();
    }

    static List<String> hints(List<String> dimensions) {
        return dimensions.stream().map(ENTRIES::get).filter(e -> e != null).map(Entry::hint).filter(h -> !h.isEmpty()).toList();
    }
}