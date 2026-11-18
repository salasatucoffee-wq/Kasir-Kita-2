package id.salasatucoffee.kasir;

import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.util.Base64;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.OutputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Kirim byte ESC/POS ke printer lewat USB host atau Bluetooth SPP. */
@CapacitorPlugin(name = "PrinterBridge")
public class PrinterBridgePlugin extends Plugin {
    private static final String ACTION_USB_PERMISSION = "id.salasatucoffee.kasir.USB_PERMISSION";
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final Object LOCK = new Object();

    private static String usbId(UsbDevice d) {
        return String.format(Locale.US, "%04x:%04x", d.getVendorId(), d.getProductId());
    }

    @PluginMethod
    public void listUsb(PluginCall call) {
        try {
            UsbManager um = (UsbManager) getContext().getSystemService(Context.USB_SERVICE);
            JSArray arr = new JSArray();
            for (UsbDevice d : um.getDeviceList().values()) {
                JSObject o = new JSObject();
                String name = null;
                try { name = d.getProductName(); } catch (Throwable t) { /* api lama */ }
                if (name == null || name.isEmpty()) name = "Perangkat USB";
                o.put("name", name);
                o.put("address", usbId(d));
                o.put("printerClass", findPrinterInterface(d) != null);
                arr.put(o);
            }
            JSObject res = new JSObject();
            res.put("devices", arr);
            call.resolve(res);
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    @PluginMethod
    public void listBluetooth(PluginCall call) {
        try {
            BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
            if (ad == null) { call.reject("Tablet ini tidak punya Bluetooth"); return; }
            if (!ad.isEnabled()) { call.reject("Bluetooth belum aktif"); return; }
            JSArray arr = new JSArray();
            Set<BluetoothDevice> bonded = ad.getBondedDevices();
            if (bonded != null) {
                for (BluetoothDevice d : bonded) {
                    JSObject o = new JSObject();
                    o.put("name", d.getName() == null ? "Tanpa nama" : d.getName());
                    o.put("address", d.getAddress());
                    arr.put(o);
                }
            }
            JSObject res = new JSObject();
            res.put("devices", arr);
            call.resolve(res);
        } catch (SecurityException e) {
            call.reject("Izin Bluetooth ditolak");
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    @PluginMethod
    public void write(PluginCall call) {
        final String type = call.getString("type", "bt");
        final String address = call.getString("address", "");
        final String data = call.getString("data", "");
        if (address.isEmpty() || data.isEmpty()) { call.reject("Printer atau data kosong"); return; }
        final byte[] bytes = Base64.decode(data, Base64.DEFAULT);
        synchronized (LOCK) {
            try {
                if ("usb".equals(type)) writeUsb(address, bytes); else writeBluetooth(address, bytes);
                call.resolve();
            } catch (Exception e) {
                call.reject(String.valueOf(e.getMessage()));
            }
        }
    }

    /* ---------------- USB ---------------- */

    private static UsbInterface findPrinterInterface(UsbDevice d) {
        UsbInterface fallback = null;
        for (int i = 0; i < d.getInterfaceCount(); i++) {
            UsbInterface intf = d.getInterface(i);
            if (findBulkOut(intf) == null) continue;
            if (intf.getInterfaceClass() == UsbConstants.USB_CLASS_PRINTER) return intf;
            if (fallback == null) fallback = intf;
        }
        return fallback;
    }

    private static UsbEndpoint findBulkOut(UsbInterface intf) {
        for (int e = 0; e < intf.getEndpointCount(); e++) {
            UsbEndpoint ep = intf.getEndpoint(e);
            if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.getDirection() == UsbConstants.USB_DIR_OUT) return ep;
        }
        return null;
    }

    private void writeUsb(String id, byte[] bytes) throws Exception {
        Context ctx = getContext();
        UsbManager um = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
        UsbDevice dev = null;
        for (UsbDevice d : um.getDeviceList().values()) {
            if (usbId(d).equalsIgnoreCase(id)) { dev = d; break; }
        }
        if (dev == null) throw new Exception("Printer USB tidak ditemukan. Cek kabel dan daya printer.");
        if (!um.hasPermission(dev)) requestUsbPermission(ctx, um, dev);
        UsbInterface intf = findPrinterInterface(dev);
        if (intf == null) throw new Exception("Perangkat USB ini tidak punya jalur cetak");
        UsbEndpoint out = findBulkOut(intf);
        UsbDeviceConnection conn = um.openDevice(dev);
        if (conn == null) throw new Exception("Tidak bisa membuka printer USB (permission)");
        try {
            if (!conn.claimInterface(intf, true)) throw new Exception("Printer USB sedang dipakai aplikasi lain");
            int off = 0;
            while (off < bytes.length) {
                int n = Math.min(4096, bytes.length - off);
                byte[] chunk = new byte[n];
                System.arraycopy(bytes, off, chunk, 0, n);
                int r = conn.bulkTransfer(out, chunk, n, 5000);
                if (r < 0) throw new Exception("Pengiriman ke printer USB gagal");
                off += r;
            }
            try { conn.releaseInterface(intf); } catch (Throwable t) { /* abaikan */ }
        } finally {
            conn.close();
        }
    }

    private void requestUsbPermission(Context ctx, UsbManager um, UsbDevice dev) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] granted = {false};
        BroadcastReceiver rx = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (ACTION_USB_PERMISSION.equals(i.getAction())) {
                    granted[0] = i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    latch.countDown();
                }
            }
        };
        ctx.registerReceiver(rx, new IntentFilter(ACTION_USB_PERMISSION));
        try {
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= 0x02000000; // FLAG_MUTABLE
            Intent intent = new Intent(ACTION_USB_PERMISSION);
            intent.setPackage(ctx.getPackageName());
            um.requestPermission(dev, PendingIntent.getBroadcast(ctx, 0, intent, flags));
            if (!latch.await(30, TimeUnit.SECONDS) || !granted[0]) throw new Exception("Izin USB ditolak");
        } finally {
            try { ctx.unregisterReceiver(rx); } catch (Throwable t) { /* abaikan */ }
        }
    }

    /* ---------------- Bluetooth ---------------- */

    private void writeBluetooth(String mac, byte[] bytes) throws Exception {
        BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        if (ad == null || !ad.isEnabled()) throw new Exception("Bluetooth belum aktif");
        BluetoothDevice dev;
        try { dev = ad.getRemoteDevice(mac); } catch (IllegalArgumentException e) { throw new Exception("Alamat printer tidak valid"); }
        try { ad.cancelDiscovery(); } catch (Throwable t) { /* abaikan */ }
        BluetoothSocket sock = null;
        try {
            try {
                sock = dev.createRfcommSocketToServiceRecord(SPP);
                sock.connect();
            } catch (Exception first) {
                try { if (sock != null) sock.close(); } catch (Throwable t) { /* abaikan */ }
                sock = dev.createInsecureRfcommSocketToServiceRecord(SPP);
                sock.connect();
            }
            OutputStream os = sock.getOutputStream();
            int off = 0;
            while (off < bytes.length) {
                int n = Math.min(512, bytes.length - off);
                os.write(bytes, off, n);
                os.flush();
                off += n;
                Thread.sleep(20);
            }
            Thread.sleep(400);
        } catch (SecurityException e) {
            throw new Exception("Izin Bluetooth ditolak");
        } finally {
            try { if (sock != null) sock.close(); } catch (Throwable t) { /* abaikan */ }
        }
    }
}
