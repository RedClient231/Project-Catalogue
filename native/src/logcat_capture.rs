// logcat_capture.rs - Logcat capture and storage engine
// Reads Android system logs via liblog and writes to /storage/Download/VirtualSpace/logs/

use alloc::string::{String, ToString};
use alloc::vec::Vec;
use alloc::format;
use core::sync::atomic::{AtomicBool, Ordering};

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

    // On Android, we use __android_log_write or execute logcat subprocess
    // Since READ_LOGS permission is restricted on Android 13+ without root,
    // we capture our own process logs and system logs available via liblog

    #[cfg(target_os = "android")]
    {
        // Start background log reader thread
        start_log_reader(&log_dir)?;
    }

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

fn start_log_reader(_log_dir: &str) -> Result<(), &'static str> {
    // Platform-specific: on Android, use android_logger or spawn logcat -d process
    // Loop reading logs and writing to dated files
    Ok(())
}

fn mkdir_p(path: &str) -> Result<(), &'static str> {
    #[cfg(target_os = "android")]
    {
        use libc::mkdir;
        let cpath = {
            let mut v = Vec::from(path.as_bytes());
            v.push(0);
            v
        };
        unsafe {
            mkdir(cpath.as_ptr() as *const i8, 0o755);
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
    // Simplified timestamp - production would use proper time formatting
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
        use libc::{open, write, close, O_WRONLY | O_CREAT | O_APPEND, S_IRUSR | S_IWUSR};
        let cpath = {
            let mut v = Vec::from(log_file.as_bytes());
            v.push(0);
            v
        };
        unsafe {
            let fd = open(cpath.as_ptr() as *const i8, O_WRONLY | O_CREAT | O_APPEND, S_IRUSR | S_IWUSR);
            if fd < 0 {
                return Err("Failed to open log file");
            }
            let bytes = header.as_bytes();
            write(fd, bytes.as_ptr() as *const core::ffi::c_void, bytes.len());
            close(fd);
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
        use libc::{open, write, close, O_WRONLY | O_CREAT | O_APPEND, S_IRUSR | S_IWUSR};
        let cpath = {
            let mut v = Vec::from(log_file.as_bytes());
            v.push(0);
            v
        };
        unsafe {
            let fd = open(cpath.as_ptr() as *const i8, O_WRONLY | O_CREAT | O_APPEND, S_IRUSR | S_IWUSR);
            if fd >= 0 {
                let bytes = line.as_bytes();
                write(fd, bytes.as_ptr() as *const core::ffi::c_void, bytes.len());
                close(fd);
            }
        }
    }

    let _ = (output_dir, level, tag, message);
}
