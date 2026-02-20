var DB_NAME = "loogbook-db";
var STORE_NAME = "directory-handles";
var HANDLE_KEY = "root-directory";

function openDB() {
  return new Promise(function(resolve, reject) {
    var request = indexedDB.open(DB_NAME, 1);
    request.onupgradeneeded = function(e) {
      var db = e.target.result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME);
      }
    };
    request.onsuccess = function(e) { resolve(e.target.result); };
    request.onerror = function(e) { reject(e.target.error); };
  });
}

function saveHandle(handle) {
  return openDB().then(function(db) {
    return new Promise(function(resolve, reject) {
      var tx = db.transaction([STORE_NAME], "readwrite");
      var store = tx.objectStore(STORE_NAME);
      var data = { handle: handle, name: handle.name };
      var request = store.put(data, HANDLE_KEY);
      request.onsuccess = function() { resolve(true); };
      request.onerror = function(e) { reject(e.error); };
    });
  });
}

var btn = document.getElementById("select-btn");
var statusEl = document.getElementById("status");

if (typeof window.showDirectoryPicker !== "function") {
  btn.disabled = true;
  statusEl.textContent = "Your browser does not support the File System Access API on extension pages. Please use Chrome.";
  statusEl.className = "status visible error";
} else {
  btn.addEventListener("click", function() {
    window.showDirectoryPicker({ mode: "readwrite" })
      .then(function(handle) {
        return saveHandle(handle).then(function() { return handle; });
      })
      .then(function(handle) {
        statusEl.textContent = 'Folder "' + handle.name + '" selected! You can close this tab and reopen the extension.';
        statusEl.className = "status visible success";
        setTimeout(function() { window.close(); }, 2500);
      })
      .catch(function(err) {
        if (err.name === "AbortError") {
          statusEl.textContent = "Selection cancelled. You can try again.";
          statusEl.className = "status visible";
        } else {
          statusEl.textContent = "Error: " + err.message;
          statusEl.className = "status visible error";
        }
      });
  });
}
