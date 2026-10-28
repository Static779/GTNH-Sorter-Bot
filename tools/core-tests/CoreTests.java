import java.util.HashMap;
import java.util.Map;

import com.whalestorm.chestsorter.core.BlockPos;
import com.whalestorm.chestsorter.core.ChestPairing;
import com.whalestorm.chestsorter.core.SorterState;

/** Plain-Java tests for the core package (no JUnit, no Minecraft). Run with tools/test-core.sh. */
public class CoreTests {

    static int failures = 0;

    public static void main(String[] args) {
        singleChestStaysSingle();
        twoTouchingChestsPair();
        chestAndTrappedChestDoNotPair();
        diagonalAndVerticalDoNotPair();
        threeInARowPairsFirstTwoOnly();
        twoSeparateDoubles();
        toggle();
        if (failures > 0) {
            System.out.println(failures + " FAILED");
            System.exit(1);
        }
        System.out.println("all core tests passed");
    }

    static final String CHEST = "chest", TRAPPED = "trapped";

    static Map<BlockPos, Object> chests(Object... xyzKey) {
        Map<BlockPos, Object> m = new HashMap<>();
        for (int i = 0; i < xyzKey.length; i += 4) {
            m.put(new BlockPos((int) xyzKey[i], (int) xyzKey[i + 1], (int) xyzKey[i + 2]), xyzKey[i + 3]);
        }
        return m;
    }

    static void singleChestStaysSingle() {
        check("single", ChestPairing.pair(chests(0, 64, 0, CHEST)).isEmpty());
    }

    static void twoTouchingChestsPair() {
        Map<BlockPos, BlockPos> p = ChestPairing.pair(chests(0, 64, 0, CHEST, 1, 64, 0, CHEST));
        check("pair x", new BlockPos(1, 64, 0).equals(p.get(new BlockPos(0, 64, 0))));
        check("pair x reverse", new BlockPos(0, 64, 0).equals(p.get(new BlockPos(1, 64, 0))));
        p = ChestPairing.pair(chests(5, 70, -3, CHEST, 5, 70, -2, CHEST));
        check("pair z", p.size() == 2);
    }

    static void chestAndTrappedChestDoNotPair() {
        check("chest+trapped", ChestPairing.pair(chests(0, 64, 0, CHEST, 1, 64, 0, TRAPPED)).isEmpty());
        check("trapped+trapped", ChestPairing.pair(chests(0, 64, 0, TRAPPED, 1, 64, 0, TRAPPED)).size() == 2);
    }

    static void diagonalAndVerticalDoNotPair() {
        check("diagonal", ChestPairing.pair(chests(0, 64, 0, CHEST, 1, 64, 1, CHEST)).isEmpty());
        check("vertical", ChestPairing.pair(chests(0, 64, 0, CHEST, 0, 65, 0, CHEST)).isEmpty());
    }

    static void threeInARowPairsFirstTwoOnly() {
        Map<BlockPos, BlockPos> p = ChestPairing.pair(chests(0, 64, 0, CHEST, 1, 64, 0, CHEST, 2, 64, 0, CHEST));
        check("three: first two paired", new BlockPos(1, 64, 0).equals(p.get(new BlockPos(0, 64, 0))));
        check("three: third alone", !p.containsKey(new BlockPos(2, 64, 0)));
    }

    static void twoSeparateDoubles() {
        Map<BlockPos, BlockPos> p = ChestPairing
            .pair(chests(0, 64, 0, CHEST, 1, 64, 0, CHEST, 0, 64, 2, CHEST, 1, 64, 2, CHEST));
        check("two doubles", p.size() == 4);
    }

    static void toggle() {
        check("IDLE->SCAN", SorterState.IDLE.toggled() == SorterState.SCAN);
        check("SCAN->IDLE", SorterState.SCAN.toggled() == SorterState.IDLE);
        check("EXECUTE->IDLE", SorterState.EXECUTE.toggled() == SorterState.IDLE);
    }

    static void check(String name, boolean ok) {
        if (!ok) {
            failures++;
            System.out.println("FAIL " + name);
        }
    }
}
