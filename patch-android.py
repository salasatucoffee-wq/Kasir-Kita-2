import re, shutil, os
pkg = 'id/salasatucoffee/kasir'
dst = 'android/app/src/main/java/' + pkg
os.makedirs(dst, exist_ok=True)
for f in ('PrinterBridgePlugin.java', 'MainActivity.java'):
    shutil.copy('native/' + f, dst + '/' + f)

m = 'android/app/src/main/AndroidManifest.xml'
s = open(m).read()
perms = ['BLUETOOTH', 'BLUETOOTH_ADMIN', 'BLUETOOTH_CONNECT', 'BLUETOOTH_SCAN']
add = ''.join('    <uses-permission android:name="android.permission.%s" />\n' % p
              for p in perms if 'android.permission.%s"' % p not in s)
add += '    <uses-feature android:name="android.hardware.usb.host" android:required="false" />\n'
add += '    <uses-feature android:name="android.hardware.bluetooth" android:required="false" />\n'
s = s.replace('<application', add + '    <application', 1)
open(m, 'w').write(s)

v = 'android/variables.gradle'
t = open(v).read()
t = re.sub(r'targetSdkVersion\s*=\s*\d+', 'targetSdkVersion = 30', t)
open(v, 'w').write(t)
print('patched')

# Tanda tangan tetap + versionCode naik, supaya APK baru bisa menimpa yang lama di tablet
g = 'android/app/build.gradle'
b = open(g).read()
sign = """    signingConfigs {
        debug {
            storeFile file('../../salasatu.keystore')
            storePassword 'salasatu123'
            keyAlias 'salasatu'
            keyPassword 'salasatu123'
        }
    }
"""
b = re.sub(r'(\nandroid \{\n)', lambda m: m.group(1) + sign, b, count=1)
b = re.sub(r'versionCode \d+', 'versionCode ' + os.environ.get('VERSION_CODE', '1'), b, count=1)
open(g, 'w').write(b)
print('signing patched')
