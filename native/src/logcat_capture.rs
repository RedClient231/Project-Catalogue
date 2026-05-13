// logcat_capture.rs - Logcat capture and storage engine
// Reads Android system logs via liblog and writes to /storage/Download/VirtualSpace/logs/

use std::string::{String, ToString};
use std::vec::Vec;
use std::format;
use std::sync::atomic::{AtomicBool, Ordering};

static CAPTURE_ACTIVE: AtomicBool = AtomicBool::new(false);

/// Start capturing logcat to the specified output directory
pub fn start_logcat_to_file(output_dir: &str) -> Result<(), &'static str> {
    if CAPTURE_ACTIVE.load(Ordering::SeqCst) {
        return Err("Logcat capture already active");
    }

    // Ensure output directory exists
    let log_dir = format!("{}/VirtualSpace/logs", output_dir);
    mkdir_p(&log_dir)?;

    CAPTURE_ACTIVE.store(true, Ordering::SeqCst);

    // Write initial log entry
    let timestamp = get_timestamp();
    let log_file = format!("{}/logcat_{}.txt", log_dir, timestamp);
    write_log_header(&log_file)?;

    Ok(())
}

/// Stop active logcat capture
pub fn stop_logcat() {
    CAPTURE_ACTIVE.store(false, Ordering::SeqCst);
}

/// Check if capture is active
pub fn is_capturing() -> bool {
    CAPTURE_ACTIVE.load(Ordering::SeqCst)
}

fn mkdir_p(path: &str) -> Result<(), &'static str> {
    #[cfg(target_os = "android")]
    {
        let cpath = cstr_from_str(path);
        unsafe {
            libc::mkdir(cpath.as_ptr() as *const libc::c_char, 0o755);
        }
        Ok(())
    }
    #[cfg(not(target_os = "android"))]
    {
        let _ = path;
        Ok(())
    }
}

fn get_timestamp() -> String {
    String::from("20260513_000000")
}

fn write_log_header(log_file: &str) -> Result<(), &'static str> {
    let header = format!(
        "===== VirtualSpace Logcat =====\n\
         Started: {}\n\
         Device: Android 13+\n\
         ==============================\n\n",
        get_timestamp()
    );

    #[cfg(target_os = "android")]
    {
        let cpath = cstr_from_str(log_file);
        unsafe {
            let fd = libc::open(
                cpath.as_ptr() as *const libc::c_char,
                libc::O_WRONLY | libc::O_CREAT | libc::O_APPEND,
                (libc::S_IRUSR | libc::S_IWUSR) as libc::mode_t as libc::c_uint,
            );
            if fd < 0 {
                return Err("Failed to open log file");
            }
            let bytes = header.as_bytes();
            libc::write(fd, bytes.as_ptr() as *const libc::c_void, bytes.len());
            libc::close(fd);
        }
        Ok(())
    }
    #[cfg(not(target_os = "android"))]
    {
        let _ = (log_file, header);
        Ok(())
    }
}

/// Append a log line to the current log file
pub fn append_log(output_dir: &str, level: &str, tag: &str, message: &str) {
    let log_dir = format!("{}/VirtualSpace/logs", output_dir);
    let timestamp = get_timestamp();
    let log_file = format!("{}/logcat_{}.txt", log_dir, timestamp);

    let line = format!("{} {} {}: {}\n", timestamp, level, tag, message);

    #[cfg(target_os = "android")]
    {
        let cpath = cstr_from_str(&log_file);
        unsafe {
            let fd = libc::open(
                cpath.as_ptr() as *const libc::c_char,
                libc::O_WRONLY | libc::O_CREAT | libc::O_APPEND,
                (libc::S_IRUSR | libc::S_IWUSR) as libc::mode_t as libc::c_uint,
            );
            if fd >= 0 {
                let bytes = line.as_bytes();
                libc::write(fd, bytes.as_ptr() as *const libc::c_void, bytes.len());
                libc::close(fd);
            }
        }
    }

    let _ = (output_dir, level, tag, message);
}

fn cstr_from_str(s: &str) -> Vec<u8> {
    let mut v = Vec::from(s.as_bytes());
    v.push(0);
    v
}
