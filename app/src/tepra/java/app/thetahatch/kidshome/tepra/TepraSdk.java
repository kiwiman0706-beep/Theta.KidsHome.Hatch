package app.thetahatch.kidshome.tepra;

import android.content.Context;
import android.graphics.Bitmap;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jp.co.kingjim.tepraprint.sdk.TepraPrint;
import jp.co.kingjim.tepraprint.sdk.TepraPrintCallback;
import jp.co.kingjim.tepraprint.sdk.TepraPrintDiscoverConnectionType;
import jp.co.kingjim.tepraprint.sdk.TepraPrintDiscoverPrinter;
import jp.co.kingjim.tepraprint.sdk.TepraPrintDiscoverPrinterCallback;
import jp.co.kingjim.tepraprint.sdk.TepraPrintParameterKey;
import jp.co.kingjim.tepraprint.sdk.TepraPrintPrintSpeed;
import jp.co.kingjim.tepraprint.sdk.TepraPrintPrintingPhase;
import jp.co.kingjim.tepraprint.sdk.TepraPrintStatusError;
import jp.co.kingjim.tepraprint.sdk.TepraPrintTapeCut;
import jp.co.kingjim.tepraprint.sdk.TepraPrintTapeWidth;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;
import kotlin.jvm.functions.Function3;

/**
 * 「声でテプラ」と おなじ SDK の つかいかた（SR5900P を Wi-Fi で）。
 * SDK の Map は 型 が はっきり しない ので Java の raw 型 で うける（どちら でも コンパイル できる）。
 */
@SuppressWarnings({"rawtypes", "unchecked"})
final class TepraSdk implements TepraBridge {
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private TepraPrintDiscoverPrinter discoverer;

    @Override public boolean getAvailable() { return true; }

    @Override
    public boolean startDiscover(Context context, final Function1 onFound) {
        try {
            stopDiscover();
            TepraPrintDiscoverPrinter d = new TepraPrintDiscoverPrinter(
                    Collections.singletonList("_pdl-datastream._tcp.local."),
                    Collections.singletonList("(KING JIM TEPRA PRO SR5900P)"),
                    EnumSet.of(TepraPrintDiscoverConnectionType.ConnectionTypeNetwork));
            d.setCallback(new TepraPrintDiscoverPrinterCallback() {
                @Override public void onFindPrinter(TepraPrintDiscoverPrinter source, Map printer) {
                    Map<String, String> m = new HashMap<>();
                    for (Object e : printer.entrySet()) {
                        Map.Entry en = (Map.Entry) e;
                        if (en.getKey() != null && en.getValue() != null) m.put(en.getKey().toString(), en.getValue().toString());
                    }
                    m.put("name", str(printer.get(TepraPrintDiscoverPrinter.PRINTER_INFO_NAME)));
                    m.put("host", str(printer.get(TepraPrintDiscoverPrinter.PRINTER_INFO_HOST)));
                    onFound.invoke(m);
                }
                @Override public void onRemovePrinter(TepraPrintDiscoverPrinter source, Map printer) { }
            });
            d.enableMulticastOnWifi(context);
            d.startDiscover(context);
            discoverer = d;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void stopDiscover() {
        try { if (discoverer != null) { discoverer.stopDiscover(); discoverer.disableMulticastOnWifi(); } } catch (Throwable ignored) { }
        discoverer = null;
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }

    private static int mm(int t) {
        if (t == TepraPrintTapeWidth.Normal_4mm) return 4;
        if (t == TepraPrintTapeWidth.Normal_6mm) return 6;
        if (t == TepraPrintTapeWidth.Normal_9mm) return 9;
        if (t == TepraPrintTapeWidth.Normal_12mm) return 12;
        if (t == TepraPrintTapeWidth.Normal_18mm) return 18;
        if (t == TepraPrintTapeWidth.Normal_24mm || t == TepraPrintTapeWidth.Cable_24mm) return 24;
        if (t == TepraPrintTapeWidth.Normal_36mm || t == TepraPrintTapeWidth.Cable_36mm) return 36;
        return 0;
    }

    private static String message(int e) {
        if (e == TepraPrintStatusError.ConnectionFailed) return "SR5900P に つながりません（おなじ Wi-Fi か たしかめて）";
        if (e == TepraPrintStatusError.NoTapeCartridge) return "テープ が 入って いません";
        if (e == TepraPrintStatusError.CoverOpen) return "カバー が あいて います";
        if (e == TepraPrintStatusError.TapeEnd) return "テープ が なくなりました";
        if (e == TepraPrintStatusError.CutterError) return "カッター の エラー です";
        if (e == TepraPrintStatusError.OtherUsing || e == TepraPrintStatusError.DeviceUsing) return "ほか で つかって います。すこし まって ね";
        if (e == TepraPrintStatusError.HeadOverheated || e == TepraPrintStatusError.TemperatureError) return "テプラ が あつく なって います";
        return "テプラ の エラー（" + e + "）";
    }

    @Override
    public void print(final Context context, final Map printer, final int copies,
                      final Function2 render,
                      final Function3 onStatus) {
        exec.execute(() -> {
            try {
                TepraPrint tp = new TepraPrint(context.getApplicationContext());
                tp.setCallback(new TepraPrintCallback() {
                    @Override public void onChangePrintOperationPhase(TepraPrint p, int phase) {
                        if (phase == TepraPrintPrintingPhase.Prepare) onStatus.invoke("じゅんび して います…", false, false);
                        else if (phase == TepraPrintPrintingPhase.Processing) onStatus.invoke("おくって います…", false, false);
                        else if (phase == TepraPrintPrintingPhase.WaitingForPrint) onStatus.invoke("いんさつ して います…", false, false);
                        else if (phase == TepraPrintPrintingPhase.Complete) onStatus.invoke("いんさつ できました", true, true);
                    }
                    @Override public void onSuspendPrintOperation(TepraPrint p, int errorStatus, int deviceStatus) {
                        p.cancelPrint();
                        onStatus.invoke(message(deviceStatus != 0 ? deviceStatus : errorStatus), true, false);
                    }
                    @Override public void onAbortPrintOperation(TepraPrint p, int errorStatus, int deviceStatus) {
                        onStatus.invoke(message(deviceStatus != 0 ? deviceStatus : errorStatus), true, false);
                    }
                    @Override public void onChangeTapeFeedOperationPhase(TepraPrint p, int phase) { }
                    @Override public void onAbortTapeFeedOperation(TepraPrint p, int errorStatus, int deviceStatus) { }
                });
                tp.setPrinterInformation((Map) new HashMap<>(printer));
                Map status = tp.fetchPrinterStatus();
                int err = (status == null || status.isEmpty()) ? TepraPrintStatusError.ConnectionFailed : tp.getDeviceErrorFromStatus(status);
                if (err != TepraPrintStatusError.NoError) { onStatus.invoke(message(err), true, false); return; }
                int tape = tp.getTapeWidthFromStatus(status);
                int width = mm(tape);
                if (width == 0) { onStatus.invoke("テープ を たしかめて ください", true, false); return; }
                int h = tp.getPrintableSizeFromTape(tape);
                if (h <= 0) { onStatus.invoke("印刷 サイズ が わかりません", true, false); return; }
                Bitmap bitmap = (Bitmap) render.invoke(h, width);
                Map params = new HashMap();
                params.put(TepraPrintParameterKey.Copies, Math.max(1, Math.min(99, copies)));
                params.put(TepraPrintParameterKey.TapeCut, TepraPrintTapeCut.EachLabel);
                params.put(TepraPrintParameterKey.HalfCut, tp.isSupportHalfCut());
                params.put(TepraPrintParameterKey.PrintSpeed, TepraPrintPrintSpeed.PrintSpeedHigh);
                params.put(TepraPrintParameterKey.Density, 0);
                params.put(TepraPrintParameterKey.TapeWidth, tape);
                params.put(TepraPrintParameterKey.PriorityPrintSetting, false);
                params.put(TepraPrintParameterKey.HalfCutContinuous, false);
                onStatus.invoke("いんさつ します（" + width + "mm）", false, false);
                tp.doPrint(bitmap, params);
            } catch (Throwable e) {
                onStatus.invoke("いんさつ できません：" + e.getMessage(), true, false);
            }
        });
    }
}
