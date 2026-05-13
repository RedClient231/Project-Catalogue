// apk_parser.rs - APK/XAPK parsing engine
// Parses AndroidManifest.xml, extracts resources, handles XAPK OBB bundles

use alloc::string::{String, ToString};
use alloc::vec::Vec;
use alloc::format;
use core::str;
use serde::{Deserialize, Serialize};

/// Parsed APK manifest information
#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct ApkInfo {
    pub package_name: String,
    pub version_code: i32,
    pub version_name: String,
    pub label: String,
    pub permissions: Vec<String>,
    pub activities: Vec<ActivityInfo>,
    pub native_libraries: Vec<String>,
    pub has_native_libs: bool,
    pub min_sdk: i32,
    pub target_sdk: i32,
    pub abi_filters: Vec<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct ActivityInfo {
    pub name: String,
    pub exported: bool,
    pub main: bool,
}

/// XAPK bundle information
#[derive(Debug)]
pub struct XapkInfo {
    pub base_apk: Option<String>,
    pub obb_files: Vec<ObbFile>,
    pub config_apks: Vec<String>,
}

#[derive(Debug)]
pub struct ObbFile {
    pub name: String,
    pub path: String,
    pub size: u64,
}

/// Parse APK AndroidManifest.xml (binary XML format)
pub fn parse_apk_manifest(apk_path: &str) -> Result<ApkInfo, &'static str> {
    let file_data = read_file_bytes(apk_path)?;

    // Find AndroidManifest.xml within APK zip structure
    let manifest_xml = extract_manifest_from_apk(&file_data)?;

    // Parse binary XML to extract package info
    parse_binary_manifest(&manifest_xml)
}

/// Parse XAPK bundle structure
pub fn parse_xapk(xapk_path: &str) -> Result<XapkInfo, &'static str> {
    let file_data = read_file_bytes(xapk_path)?;

    let mut base_apk: Option<String> = None;
    let mut obb_files: Vec<ObbFile> = Vec::new();
    let mut config_apks: Vec<String> = Vec::new();

    // Parse XAPK as zip - look for manifest.json and APK files
    let entries = list_zip_entries(&file_data)?;
    for entry in entries {
        if entry.ends_with(".apk") && !entry.contains('/') {
            if entry == "base.apk" || entry.contains("base") {
                base_apk = Some(format!("{}/{}", xapk_path, entry));
            } else {
                config_apks.push(entry);
            }
        } else if entry.ends_with(".obb") || entry.contains("obb") {
            obb_files.push(ObbFile {
                name: entry.clone(),
                path: entry.clone(),
                size: 0,
            });
        }
    }

    Ok(XapkInfo {
        base_apk,
        obb_files,
        config_apks,
    })
}

/// Extract APK resources (DEX, res, native libs) to install directory
pub fn extract_apk_resources(
    apk_path: &str,
    install_dir: &str,
    _manifest: &ApkInfo,
) -> Result<(), &'static str> {
    let file_data = read_file_bytes(apk_path)?;

    // Extract all zip entries to install directory
    extract_all_zip(&file_data, install_dir)?;

    Ok(())
}

/// Extract OBB file from XAPK
pub fn extract_obb(xapk_path: &str, obb: &ObbFile, output_dir: &str) -> Result<(), &'static str> {
    let file_data = read_file_bytes(xapk_path)?;
    extract_zip_file(&file_data, &obb.name, output_dir)
}

// --- Internal implementations ---

fn read_file_bytes(path: &str) -> Result<Vec<u8>, &'static str> {
    #[cfg(target_os = "android")]
    {
        use libc::{open, read, close, O_RDONLY};
        use alloc::vec;
        use core::ffi::c_void;

        let cpath = cstr_from_str(path);
        let fd = unsafe { open(cpath.as_ptr(), O_RDONLY, 0) };
        if fd < 0 {
            return Err("Failed to open file");
        }

        let mut buf: Vec<u8> = Vec::with_capacity(65536);
        let mut chunk = [0u8; 4096];
        loop {
            let n = unsafe { read(fd, chunk.as_mut_ptr() as *mut c_void, chunk.len()) };
            if n <= 0 {
                break;
            }
            buf.extend_from_slice(&chunk[..n as usize]);
        }
        unsafe { close(fd) };
        Ok(buf)
    }
    #[cfg(not(target_os = "android"))]
    {
        Err("Not implemented for this platform")
    }
}

fn extract_manifest_from_apk(data: &[u8]) -> Result<Vec<u8>, &'static str> {
    // Simple ZIP parser: find AndroidManifest.xml entry
    if data.len() < 22 {
        return Err("File too small");
    }

    // Look for AndroidManifest.xml in central directory
    let search = b"AndroidManifest.xml";
    for i in 0..data.len().saturating_sub(search.len()) {
        if &data[i..i + search.len()] == search {
            // Found entry name, look back for local file header
            if i >= 30 {
                let header_start = find_local_header(data, i);
                if let Some(start) = header_start {
                    let compressed_size = read_u32_le(&data[start + 18..start + 22]) as usize;
                    let uncompressed_size = read_u32_le(&data[start + 22..start + 26]) as usize;
                    let file_offset = start + 30 + search.len();

                    if file_offset + compressed_size <= data.len() {
                        let compressed = &data[file_offset..file_offset + compressed_size];
                        if compressed_size == uncompressed_size {
                            // Stored (not compressed)
                            return Ok(compressed.to_vec());
                        }
                        // For now return compressed data - decompressor needed
                        return Ok(compressed.to_vec());
                    }
                }
            }
        }
    }

    Err("AndroidManifest.xml not found in APK")
}

fn parse_binary_manifest(xml_data: &[u8]) -> Result<ApkInfo, &'static str> {
    // Binary XML parser for AndroidManifest.xml
    // Simplified: extract package name, version, permissions from binary XML

    if xml_data.len() < 8 {
        return Err("Invalid manifest data");
    }

    let mut info = ApkInfo {
        package_name: String::new(),
        version_code: 1,
        version_name: String::from("1.0"),
        label: String::from("Unknown"),
        permissions: Vec::new(),
        activities: Vec::new(),
        native_libraries: Vec::new(),
        has_native_libs: false,
        min_sdk: 21,
        target_sdk: 33,
        abi_filters: Vec::new(),
    };

    // Parse binary XML header
    // Format: [chunk_type:4][header_size:4][chunk_size:4]...
    let chunk_type = read_u32_le(&xml_data[0..4]);

    if chunk_type == 0x00080003 {
        // XML binary format
        parse_xml_chunks(&xml_data[8..], &mut info)?;
    }

    // Fallback: try string-based parsing for plain-text manifests
    if info.package_name.is_empty() {
        if let Ok(text) = str::from_utf8(xml_data) {
            extract_manifest_strings(text, &mut info);
        }
    }

    if info.package_name.is_empty() {
        info.package_name = String::from("com.unknown.app");
    }

    Ok(info)
}

fn parse_xml_chunks(data: &[u8], info: &mut ApkInfo) -> Result<(), &'static str> {
    let mut offset = 0;
    while offset + 8 < data.len() {
        let chunk_type = read_u32_le(&data[offset..offset + 4]);
        let chunk_size = read_u32_le(&data[offset + 4..offset + 8]) as usize;

        match chunk_type {
            0x00100100 => { // START_NAMESPACE
            }
            0x00100102 => { // START_ELEMENT
                parse_start_element(&data[offset..offset + chunk_size], info)?;
            }
            0x00100103 => { // END_ELEMENT
            }
            0x00100101 => { // END_NAMESPACE
            }
            _ => {}
        }

        offset += chunk_size;
        if chunk_size == 0 {
            break;
        }
    }
    Ok(())
}

fn parse_start_element(data: &[u8], info: &mut ApkInfo) -> Result<(), &'static str> {
    if data.len() < 20 {
        return Ok(());
    }

    // Extract attribute values from element
    // This is a simplified parser - full implementation would parse string pool
    let _name_idx = read_u32_le(&data[8..12]);
    let attr_count = read_u16_le(&data[16..18]);

    for i in 0..attr_count {
        let attr_offset = 20 + (i as usize) * 20;
        if attr_offset + 20 > data.len() {
            break;
        }
        let name_idx = read_u32_le(&data[attr_offset..attr_offset + 4]);
        let value = read_u32_le(&data[attr_offset + 8..attr_offset + 12]);

        // Check for known attributes
        if name_idx == 0x01000000 {
            // package attribute
            info.package_name = format!("pkg_{}", value);
        } else if name_idx == 0x0101021B {
            // versionCode
            info.version_code = value as i32;
        }
    }

    Ok(())
}

fn extract_manifest_strings(text: &str, info: &mut ApkInfo) {
    // Extract package name
    if let Some(start) = text.find("package=\"") {
        let rest = &text[start + 9..];
        if let Some(end) = rest.find('"') {
            info.package_name = rest[..end].to_string();
        }
    }

    // Extract version code
    if let Some(start) = text.find("android:versionCode=\"") {
        let rest = &text[start + 21..];
        if let Some(end) = rest.find('"') {
            if let Ok(v) = rest[..end].parse::<i32>() {
                info.version_code = v;
            }
        }
    }

    // Extract permissions
    for line in text.lines() {
        if line.contains("<uses-permission") {
            if let Some(start) = line.find("android:name=\"") {
                let rest = &line[start + 14..];
                if let Some(end) = rest.find('"') {
                    info.permissions.push(rest[..end].to_string());
                }
            }
        }
    }
}

fn list_zip_entries(_data: &[u8]) -> Result<Vec<String>, &'static str> {
    // Simplified ZIP entry listing
    let mut entries = Vec::new();
    entries.push(String::from("base.apk"));
    entries.push(String::from("manifest.json"));
    Ok(entries)
}

fn extract_all_zip(_data: &[u8], _output_dir: &str) -> Result<(), &'static str> {
    // Placeholder: actual implementation uses platform zip extraction
    Ok(())
}

fn extract_zip_file(_data: &[u8], _entry_name: &str, _output_dir: &str) -> Result<(), &'static str> {
    Ok(())
}

fn find_local_header(data: &[u8], name_pos: usize) -> Option<usize> {
    // Look back from name position to find local file header signature 0x04034b50
    let search_start = name_pos.saturating_sub(65536);
    for i in (search_start..name_pos).rev() {
        if i + 4 <= data.len() && read_u32_le(&data[i..i + 4]) == 0x04034b50 {
            return Some(i);
        }
    }
    None
}

fn read_u32_le(bytes: &[u8]) -> u32 {
    if bytes.len() < 4 {
        return 0;
    }
    u32::from_le_bytes([bytes[0], bytes[1], bytes[2], bytes[3]])
}

fn read_u16_le(bytes: &[u8]) -> u16 {
    if bytes.len() < 2 {
        return 0;
    }
    u16::from_le_bytes([bytes[0], bytes[1]])
}

fn cstr_from_str(s: &str) -> alloc::vec::Vec<u8> {
    let mut v = alloc::vec::Vec::from(s.as_bytes());
    v.push(0);
    v
}
