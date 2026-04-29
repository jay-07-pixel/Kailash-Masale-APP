package com.example.kailashmasale;

import android.graphics.Color;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shared logic for Expenditure documents: TA row color (red/blue) and blue-only totals.
 */
public final class ExpenditureTaUtils {

    private static final Set<String> SKIP_KEYS = new HashSet<>(Arrays.asList(
            "employeeId", "employeeName", "employeeRole", "month", "monthIndex", "salary", "totals", "year", "updatedAt"));

    private ExpenditureTaUtils() {}

    /**
     * Sum of TA amounts that are marked blue (type / color 2) in daily rows.
     * If there are no daily rows, uses {@code totals.ta} only when that total is classified as blue.
     */
    public static int sumBlueTaFromExpenditureData(Map<String, Object> data) {
        if (data == null) return 0;
        int[] blueSum = {0};
        boolean[] anyDaily = {false};
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (SKIP_KEYS.contains(entry.getKey())) continue;
            Object val = entry.getValue();
            if (val instanceof Map) {
                considerDayMap((Map<?, ?>) val, blueSum, anyDaily);
            } else if (val instanceof List) {
                for (Object item : (List<?>) val) {
                    if (item instanceof Map) {
                        considerDayMap((Map<?, ?>) item, blueSum, anyDaily);
                    }
                }
            }
        }
        if (!anyDaily[0]) {
            Object totalsObj = data.get("totals");
            if (totalsObj instanceof Map) {
                Map<?, ?> tm = (Map<?, ?>) totalsObj;
                Object taObj = tm.get("ta");
                if (taObj instanceof Number && resolveTaColorKind(tm) == 2) {
                    return ((Number) taObj).intValue();
                }
            }
        }
        return blueSum[0];
    }

    private static void considerDayMap(Map<?, ?> map, int[] blueSum, boolean[] anyDaily) {
        String dateLabel = getStringFromMap(map, "dateLabel");
        if (dateLabel == null) dateLabel = getStringFromMap(map, "date");
        String distName = getStringFromMap(map, "distName");
        if (distName == null) distName = getStringFromMap(map, "distributor");
        String bitName = getStringFromMap(map, "bitName");
        if (bitName == null) bitName = getStringFromMap(map, "bit");
        if (bitName == null) bitName = getStringFromMap(map, "beatName");
        int ta = getTaFromMap(map);
        if (dateLabel != null || distName != null || bitName != null || ta > 0) {
            anyDaily[0] = true;
            if (resolveTaColorKind(map) == 2) {
                blueSum[0] += ta;
            }
        }
    }

    /**
     * 0 = default gray, 1 = red, 2 = blue (matches Expenditure / admin).
     */
    public static int resolveTaColorKind(Map<?, ?> map) {
        if (map == null) return 0;
        String[] typeKeys = {"type", "taType", "category", "taCategory"};
        for (String key : typeKeys) {
            Object o = map.get(key);
            if (o instanceof Number) {
                int n = ((Number) o).intValue();
                if (n == 1) return 1;
                if (n == 2) return 2;
            } else if (o != null) {
                String s = String.valueOf(o).trim().toLowerCase(Locale.US);
                if ("1".equals(s) || "red".equals(s)) return 1;
                if ("2".equals(s) || "blue".equals(s)) return 2;
            }
        }
        String[] colorKeys = {"taColor", "color", "taColour", "fontColor"};
        for (String key : colorKeys) {
            String c = getStringFromMap(map, key);
            if (c == null || c.isEmpty()) continue;
            String low = c.toLowerCase(Locale.US);
            if (low.contains("red")) return 1;
            if (low.contains("blue")) return 2;
            if (c.startsWith("#")) {
                try {
                    int argb = Color.parseColor(c);
                    int r = Color.red(argb);
                    int b = Color.blue(argb);
                    if (r > b + 40) return 1;
                    if (b > r + 40) return 2;
                } catch (IllegalArgumentException ignored) { /* ignore */ }
            }
        }
        Object approved = map.get("approved");
        if (approved instanceof Boolean) {
            return ((Boolean) approved) ? 2 : 1;
        }
        String status = getStringFromMap(map, "status");
        if (status != null) {
            String sl = status.toLowerCase(Locale.US);
            if (sl.contains("approv") || sl.contains("confirm") || sl.contains("paid")) return 2;
            if (sl.contains("pend") || sl.contains("reject") || sl.contains("draft")) return 1;
        }
        return 0;
    }

    /**
     * Color for the DA column: daColor, nested daPresentation.color, daType, then row-level TA rules.
     */
    public static int resolveDaColorKind(Map<?, ?> map) {
        int k = kindFromKeys(map, "daColor", "daColour", "daType", "daCategory");
        if (k != 0) return k;
        Object daPres = map != null ? map.get("daPresentation") : null;
        if (daPres instanceof Map) {
            k = kindFromKeys((Map<?, ?>) daPres, "color", "colour", "class");
            if (k != 0) return k;
        }
        return resolveTaColorKind(map);
    }

    /**
     * Color for the N/H column: nhColor, nested nhPresentation.color, then row-level rules.
     */
    public static int resolveNhColorKind(Map<?, ?> map) {
        int k = kindFromKeys(map, "nhColor", "nhColour", "nhType", "nighthaultColor", "nightHaultColor", "nighthaultType", "nighthaultCategory", "nhCategory");
        if (k != 0) return k;
        Object nhPres = map != null ? map.get("nhPresentation") : null;
        if (nhPres instanceof Map) {
            k = kindFromKeys((Map<?, ?>) nhPres, "color", "colour", "class");
            if (k != 0) return k;
        }
        return resolveTaColorKind(map);
    }

    private static int kindFromKeys(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object o = map.get(key);
            if (o == null) continue;
            int k = kindFromScalarValue(o);
            if (k != 0) return k;
        }
        return 0;
    }

    private static int kindFromScalarValue(Object o) {
        if (o instanceof Number) {
            int n = ((Number) o).intValue();
            if (n == 1) return 1;
            if (n == 2) return 2;
        }
        String s = String.valueOf(o).trim();
        String low = s.toLowerCase(Locale.US);
        if ("1".equals(low) || "red".equals(low)) return 1;
        if ("2".equals(low) || "blue".equals(low)) return 2;
        if (s.startsWith("#")) {
            try {
                int argb = Color.parseColor(s);
                int r = Color.red(argb);
                int b = Color.blue(argb);
                if (r > b + 40) return 1;
                if (b > r + 40) return 2;
            } catch (IllegalArgumentException ignored) { /* ignore */ }
        }
        if (low.contains("red")) return 1;
        if (low.contains("blue")) return 2;
        return 0;
    }

    /**
     * TA rupees for a row: {@code taDisplay}, {@code taPresentation.display}, then {@code ta}.
     */
    public static int getTaFromMap(Map<?, ?> map) {
        if (map == null) return 0;
        if (map.get("taDisplay") != null) {
            return getIntFromMap(map, "taDisplay");
        }
        Object taPres = map.get("taPresentation");
        if (taPres instanceof Map) {
            Map<?, ?> pm = (Map<?, ?>) taPres;
            if (pm.get("display") != null) {
                return getIntFromMap(pm, "display");
            }
        }
        if (map.get("ta") != null) return getIntFromMap(map, "ta");
        if (map.get("TA") != null) return getIntFromMap(map, "TA");
        return 0;
    }

    /** Sum of all TA display amounts for the month (dashboard card). */
    public static int sumAllTaFromExpenditureData(Map<String, Object> data) {
        if (data == null) return 0;
        int sum = 0;
        boolean anyDaily = false;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (SKIP_KEYS.contains(entry.getKey())) continue;
            Object val = entry.getValue();
            if (val instanceof Map) {
                Map<?, ?> m = (Map<?, ?>) val;
                if (isTaDayRow(m)) {
                    anyDaily = true;
                    sum += getTaFromMap(m);
                }
            } else if (val instanceof List) {
                for (Object item : (List<?>) val) {
                    if (item instanceof Map) {
                        Map<?, ?> m = (Map<?, ?>) item;
                        if (isTaDayRow(m)) {
                            anyDaily = true;
                            sum += getTaFromMap(m);
                        }
                    }
                }
            }
        }
        if (!anyDaily) {
            Object totalsObj = data.get("totals");
            if (totalsObj instanceof Map) {
                return getTaFromMap((Map<?, ?>) totalsObj);
            }
        }
        return sum;
    }

    /** Sum of all DA display amounts for the month (dashboard card). */
    public static int sumAllDaFromExpenditureData(Map<String, Object> data) {
        if (data == null) return 0;
        int sum = 0;
        boolean anyDaily = false;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (SKIP_KEYS.contains(entry.getKey())) continue;
            Object val = entry.getValue();
            if (val instanceof Map) {
                Map<?, ?> m = (Map<?, ?>) val;
                if (isDaDayRow(m)) {
                    anyDaily = true;
                    sum += getDaFromMap(m);
                }
            } else if (val instanceof List) {
                for (Object item : (List<?>) val) {
                    if (item instanceof Map) {
                        Map<?, ?> m = (Map<?, ?>) item;
                        if (isDaDayRow(m)) {
                            anyDaily = true;
                            sum += getDaFromMap(m);
                        }
                    }
                }
            }
        }
        if (!anyDaily) {
            Object totalsObj = data.get("totals");
            if (totalsObj instanceof Map) {
                return getDaFromMap((Map<?, ?>) totalsObj);
            }
        }
        return sum;
    }

    private static boolean isTaDayRow(Map<?, ?> map) {
        String dateLabel = getStringFromMap(map, "dateLabel");
        if (dateLabel == null) dateLabel = getStringFromMap(map, "date");
        String distName = getStringFromMap(map, "distName");
        if (distName == null) distName = getStringFromMap(map, "distributor");
        String bitName = getStringFromMap(map, "bitName");
        if (bitName == null) bitName = getStringFromMap(map, "bit");
        if (bitName == null) bitName = getStringFromMap(map, "beatName");
        int ta = getTaFromMap(map);
        return dateLabel != null || distName != null || bitName != null || ta > 0;
    }

    private static boolean isDaDayRow(Map<?, ?> map) {
        String dateLabel = getStringFromMap(map, "dateLabel");
        if (dateLabel == null) dateLabel = getStringFromMap(map, "date");
        String distName = getStringFromMap(map, "distName");
        if (distName == null) distName = getStringFromMap(map, "distributor");
        int da = getDaFromMap(map);
        int nh = getNhFromMap(map);
        return dateLabel != null || distName != null || da != 0 || nh != 0;
    }

    /**
     * DA amount shown in the app. Prefer admin "display" fields when raw {@code da} is 0 but withheld/display differs
     * (e.g. {@code daDisplay}, {@code daPresentation.display}).
     */
    public static int getDaFromMap(Map<?, ?> map) {
        if (map == null) return 0;
        if (map.get("daDisplay") != null) {
            return getIntFromMap(map, "daDisplay");
        }
        Object daPres = map.get("daPresentation");
        if (daPres instanceof Map) {
            Map<?, ?> pm = (Map<?, ?>) daPres;
            if (pm.get("display") != null) {
                return getIntFromMap(pm, "display");
            }
        }
        if (map.get("da") != null) return getIntFromMap(map, "da");
        if (map.get("DA") != null) return getIntFromMap(map, "DA");
        if (map.get("dailyAllowance") != null) return getIntFromMap(map, "dailyAllowance");
        return 0;
    }

    /**
     * N/H amount: prefer {@code nhDisplay}, {@code nhPresentation.display}, then raw nh / nighthault fields.
     */
    public static int getNhFromMap(Map<?, ?> map) {
        if (map == null) return 0;
        if (map.get("nhDisplay") != null) {
            return getIntFromMap(map, "nhDisplay");
        }
        Object nhPres = map.get("nhPresentation");
        if (nhPres instanceof Map) {
            Map<?, ?> pm = (Map<?, ?>) nhPres;
            if (pm.get("display") != null) {
                return getIntFromMap(pm, "display");
            }
        }
        String[] keys = {"nh", "nighthault", "nightHalt", "night_halt", "Nighthault", "nhAmount", "NH", "nH"};
        for (String k : keys) {
            if (map.get(k) != null) return getIntFromMap(map, k);
        }
        return 0;
    }

    private static String getStringFromMap(Map<?, ?> map, String key) {
        Object o = map.get(key);
        return o != null ? String.valueOf(o).trim() : null;
    }

    private static int getIntFromMap(Map<?, ?> map, String key) {
        Object o = map.get(key);
        if (o instanceof Number) return ((Number) o).intValue();
        if (o != null) {
            try {
                return Integer.parseInt(String.valueOf(o).trim());
            } catch (NumberFormatException ignored) { /* ignore */ }
        }
        return 0;
    }
}
