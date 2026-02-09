R"(#pragma OPENCL EXTENSION cl_khr_fp16 : enable
)"
R"(
)"
R"(//------------------------------------------------------------------------------
)"
R"(// scale
)"
R"(//------------------------------------------------------------------------------
)"
R"(kernel void kernel_scale(
)"
R"(        global float4 * src0,
)"
R"(        ulong offset0,
)"
R"(        global float4 * dst,
)"
R"(        ulong offsetd,
)"
R"(        float scale
)"
R"() {
)"
R"(    src0 = (global float4*)((global char*)src0 + offset0);
)"
R"(    dst = (global float4*)((global char*)dst + offsetd);
)"
R"(    dst[get_global_id(0)] = src0[get_global_id(0)] * scale;
)"
R"(}
)"
