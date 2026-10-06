#!/usr/bin/env bash
#
# Builds the native executables (tun2socks, pdnsd) for all ABIs and installs
# them into jniLibs/<abi>/ as lib*.so so the package installer extracts them
# to nativeLibraryDir.
#
# NDK location: $ANDROID_NDK_HOME, falling back to
# $HOME/Android/Sdk/ndk/27.0.12077973.

set -euo pipefail

cd "$(dirname "$0")"

NDK="${ANDROID_NDK_HOME:-$HOME/Android/Sdk/ndk/27.0.12077973}"
NDK_BUILD="$NDK/ndk-build"
READELF="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
ABIS=(armeabi-v7a arm64-v8a x86 x86_64)

if [[ ! -x "$NDK_BUILD" ]]; then
	echo "error: ndk-build not found at $NDK_BUILD (set ANDROID_NDK_HOME)" >&2
	exit 1
fi

cleanup() {
	rm -rf obj libs jniLibs.new jniLibs.old
}
trap cleanup EXIT

cleanup

"$NDK_BUILD" NDK_DEBUG=0 NDK_OUT=obj NDK_LIBS_OUT=libs -j"$(nproc)"

mkdir jniLibs.new
for abi in "${ABIS[@]}"; do
	mkdir "jniLibs.new/$abi"
	mv "libs/$abi/tun2socks" "jniLibs.new/$abi/libtun2socks.so"
	mv "libs/$abi/pdnsd" "jniLibs.new/$abi/libpdnsd.so"
done

# Every LOAD segment must be aligned to at least 16 KB (Android 15+ / Play).
if [[ -x "$READELF" ]]; then
	for f in jniLibs.new/*/*.so; do
		while read -r align; do
			if (( align < 0x4000 )); then
				echo "error: $f has LOAD alignment $align (< 0x4000)" >&2
				exit 1
			fi
		done < <("$READELF" -lW "$f" | awk '$1 == "LOAD" { print $NF }')
	done
fi

# Swap the new libraries in, restoring the old tree if the swap fails.
if [[ -d jniLibs ]]; then
	mv jniLibs jniLibs.old
fi
if ! mv jniLibs.new jniLibs; then
	[[ -d jniLibs.old ]] && mv jniLibs.old jniLibs
	exit 1
fi

echo "Installed:"
ls -l jniLibs/*/
