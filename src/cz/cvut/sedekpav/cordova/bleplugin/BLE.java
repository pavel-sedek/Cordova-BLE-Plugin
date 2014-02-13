package cz.cvut.sedekpav.cordova.bleplugin;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

public class BLE {
	private static final int REQUEST_ENABLE_BT = 2;
	private static final long SCAN_PERIOD = 10000;
	private static final long SCAN_SERVICES_PERIOD = 10000;
	
	private Activity parent;
	
	private BluetoothManager bluetoothManager;
	private BluetoothAdapter mBluetoothAdapter;
	private BLEService mBluetoothLeService;
	
	private boolean mScanning;
    private Handler mHandler;
    
    private List<BluetoothDevice> devices;
    
	
	public BLE(Activity parent){
		bluetoothManager =
		        (BluetoothManager) parent.getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = bluetoothManager.getAdapter();
		if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
		    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
		    parent.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}
		this.parent=parent;
		mHandler = new Handler();
	}
	
	public List<BluetoothDevice> listBluetoothDevices(){
		scanLeDevice(true);
		try {
			while(mScanning){
					Thread.sleep(100);	
			}
		} catch (InterruptedException e) {
			scanLeDevice(false);
		}
		return devices;
	}
	
	public List<BluetoothGattService> listGattServices(String deviceAddress){
		ServiceConnection sc=connect(deviceAddress);
		if(!waitForServices())return null;
		List<BluetoothGattService> ret=mBluetoothLeService.getSupportedGattServices();
		disconnect(sc);
        return ret;
	}
	
	public BluetoothGattCharacteristic readCharacteristic(String deviceAddress, String serviceUUID, String charUUID){
		ServiceConnection sc=connect(deviceAddress);
		if(!waitForServices())return null;
		List<BluetoothGattService> svcs=mBluetoothLeService.getSupportedGattServices();
		BluetoothGattCharacteristic characteristic=null;
		search: for(BluetoothGattService s:svcs){
			if(s.getUuid().toString().equalsIgnoreCase(serviceUUID)){
				for(BluetoothGattCharacteristic c:s.getCharacteristics()){
					if(c.getUuid().toString().equalsIgnoreCase(charUUID)){
						characteristic=c;
						break search;
					}
				}
			}
		}
		if(characteristic==null)return null;
		mBluetoothLeService.readCharacteristic(characteristic);
		try {
			int x=0;
			while(mBluetoothLeService.getReadCharacteristic()==null){
				Thread.sleep(100);
				if((x++)*100>SCAN_SERVICES_PERIOD)return null;
			}
		} catch (InterruptedException e) {
		}
		BluetoothGattCharacteristic ret=mBluetoothLeService.getReadCharacteristic();
		disconnect(sc);
        return ret;
	}
	
	private boolean waitForServices(){
		try {
			int x=0;
			while(mBluetoothLeService==null || !mBluetoothLeService.isServicesDiscovered()){
				Thread.sleep(100);
				if((x++)*100>SCAN_SERVICES_PERIOD)return false;
			}
		} catch (InterruptedException e) {
		}
		return true;
	}
	
	private ServiceConnection connect(String deviceAddress){
		mBluetoothLeService=null;
		Intent gattServiceIntent = new Intent(parent, BLEService.class);
		ServiceConnection sc=new BLEServiceConnection(deviceAddress);
		//parent.startService(gattServiceIntent);
		parent.bindService(gattServiceIntent, sc, Context.BIND_AUTO_CREATE);
		return sc;
	}
	
	private void disconnect(ServiceConnection sc){
		mBluetoothLeService.disconnect();
		parent.unbindService(sc);
        mBluetoothLeService = null;
	}
	
	private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    parent.invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);

            mScanning = true;
            devices=new ArrayList<BluetoothDevice>();
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
        parent.invalidateOptionsMenu();
    }
	
	// Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            parent.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                	if(!devices.contains(device)){
                		devices.add(device);
                	}
                }
            });
        }
    };
    
 // Code to manage Service lifecycle.
    private final class BLEServiceConnection implements ServiceConnection {
    	
    	private String deviceAddress;
    	
    	public BLEServiceConnection(String address){
    		deviceAddress=address;
    	}

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BLEService.LocalBinder) service).getService();
            mBluetoothLeService.initialize(bluetoothManager,mBluetoothAdapter);
            Log.i("BLE", "Connectiong to device "+deviceAddress+"...");
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(deviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };
    
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}
