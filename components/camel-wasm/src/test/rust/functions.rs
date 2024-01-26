//
// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License.  You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

use std::mem;
use std::slice;
use std::collections::HashMap;

use serde::{Deserialize, Serialize};
use base64_serde::base64_serde_type;

base64_serde_type!(Base64Standard, base64::engine::general_purpose::STANDARD);

#[derive(Serialize, Deserialize)]
struct Message {
    headers: HashMap<String, serde_json::Value>,

    #[serde(with = "Base64Standard")]
    body: Vec<u8>,
}

#[cfg_attr(all(target_arch = "wasm32"), export_name = "alloc")]
#[no_mangle]
pub extern "C" fn alloc(size: u32) -> *mut u8 {
    let mut buf = Vec::with_capacity(size as usize);
    let ptr = buf.as_mut_ptr();

    // tell Rust not to clean this up
    mem::forget(buf);

    ptr
}

#[cfg_attr(all(target_arch = "wasm32"), export_name = "dealloc")]
#[no_mangle]
pub unsafe extern "C" fn dealloc(ptr: &mut u8, len: i32) {
    // Retakes the pointer which allows its memory to be freed.
    let _ = Vec::from_raw_parts(ptr, 0, len as usize);
}

#[cfg_attr(all(target_arch = "wasm32"), export_name = "process")]
#[no_mangle]
pub extern fn process(ptr: u32, len: u32) -> u64 {
    let bytes = unsafe {
        slice::from_raw_parts_mut(
            ptr as *mut u8,
            len as usize)
    };

    let mut msg: Message = serde_json::from_slice(bytes).unwrap();
    msg.body = String::from_utf8(msg.body).unwrap().to_uppercase().as_bytes().to_vec();

    let out_vec = serde_json::to_vec(&msg).unwrap();
    let out_len = out_vec.len();
    let out_ptr = alloc(out_len as u32);

    unsafe {
        std::ptr::copy_nonoverlapping(
            out_vec.as_ptr(),
            out_ptr,
            out_len as usize)
    };

    return ((out_ptr as u64) << 32) | out_len as u64;
}


#[cfg_attr(all(target_arch = "wasm32"), export_name = "process_err")]
#[no_mangle]
pub extern fn process_err(ptr: u32, len: u32) -> u64 {
    return fail(ptr, len)
}

#[cfg_attr(all(target_arch = "wasm32"), export_name = "transform")]
#[no_mangle]
pub extern fn transform(ptr: u32, len: u32) -> u64 {
    let bytes = unsafe {
        slice::from_raw_parts_mut(
            ptr as *mut u8,
            len as usize)
    };

    let msg: Message = serde_json::from_slice(bytes).unwrap();
    let res = String::from_utf8(msg.body).unwrap().to_uppercase().as_bytes().to_vec();

    let out_len = res.len();
    let out_ptr = alloc(out_len as u32);

    unsafe {
        std::ptr::copy_nonoverlapping(
            res.as_ptr(),
            out_ptr,
            out_len as usize)
    };

    return ((out_ptr as u64) << 32) | out_len as u64;
}

#[cfg_attr(all(target_arch = "wasm32"), export_name = "transform_err")]
#[no_mangle]
pub extern fn transform_err(ptr: u32, len: u32) -> u64 {
    return fail(ptr, len)
}


pub fn fail(_ptr: u32, _len: u32) -> u64 {
    let res = "this is an error";
    let mut out_len = res.len();
    let out_ptr = alloc(out_len as u32);

    unsafe {
        std::ptr::copy_nonoverlapping(
            res.as_ptr(),
            out_ptr,
            out_len as usize)
    };

    out_len |= 1 << 31;

    return ((out_ptr as u64) << 32) | out_len as u64;
}