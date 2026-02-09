R"(#pragma OPENCL EXTENSION cl_khr_fp16 : enable
)"
R"(
)"
R"(#ifdef cl_intel_subgroups
)"
R"(#pragma OPENCL EXTENSION cl_intel_subgroups : enable
)"
R"(#else
)"
R"(#pragma OPENCL EXTENSION cl_khr_subgroups : enable
)"
R"(#endif
)"
R"(
)"
R"(#ifdef cl_intel_required_subgroup_size
)"
R"(#pragma OPENCL EXTENSION cl_intel_required_subgroup_size : enable
)"
R"(#define INTEL_GPU 1
)"
R"(#define REQD_SUBGROUP_SIZE_16 __attribute__((intel_reqd_sub_group_size(16)))
)"
R"(#define REQD_SUBGROUP_SIZE_32 __attribute__((intel_reqd_sub_group_size(32)))
)"
R"(#elif defined(cl_qcom_reqd_sub_group_size)
)"
R"(#pragma OPENCL EXTENSION cl_qcom_reqd_sub_group_size : enable
)"
R"(#define ADRENO_GPU 1
)"
R"(#define REQD_SUBGROUP_SIZE_64  __attribute__((qcom_reqd_sub_group_size("half")))
)"
R"(#define REQD_SUBGROUP_SIZE_128 __attribute__((qcom_reqd_sub_group_size("full")))
)"
R"(#endif
)"
R"(
)"
R"(#ifdef ADRENO_GPU
)"
R"(REQD_SUBGROUP_SIZE_64
)"
R"(#endif
)"
R"(kernel void kernel_soft_max_f16(
)"
R"(        global float * src0,
)"
R"(        ulong offset0,
)"
R"(        global half * src1,
)"
R"(        ulong offset1,
)"
R"(        global float * dst,
)"
R"(        ulong offsetd,
)"
R"(        int ne00,
)"
R"(        int ne01,
)"
R"(        int ne02,
)"
R"(        float scale,
)"
R"(        float max_bias,
)"
R"(        float m0,
)"
R"(        float m1,
)"
R"(        int n_head_log2
)"
R"() {
)"
R"(    src0 = (global float *)((global char *)src0 + offset0);
)"
R"(    src1 = (global half *)((global char *)src1 + offset1);
)"
R"(    dst = (global float *)((global char *)dst + offsetd);
)"
R"(
)"
R"(    int i03 = get_group_id(2);
)"
R"(    int i02 = get_group_id(1);
)"
R"(    int i01 = get_group_id(0);
)"
R"(
)"
R"(    global float * psrc0 = src0 + i03*ne02*ne01*ne00 + i02*ne01*ne00 + i01*ne00;
)"
R"(    global half  * pmask = (global char *)src1 != (global char *)src0 ? src1 + i01*ne00 : 0;
)"
R"(    global float * pdst  = dst  + i03*ne02*ne01*ne00 + i02*ne01*ne00 + i01*ne00;
)"
R"(
)"
R"(    float slope = 1.0f;
)"
R"(
)"
R"(    // ALiBi
)"
R"(    if (max_bias > 0.0f) {
)"
R"(        int h = i02;
)"
R"(
)"
R"(        float base = h < n_head_log2 ? m0 : m1;
)"
R"(        int   exp  = h < n_head_log2 ? h + 1 : 2*(h - n_head_log2) + 1;
)"
R"(
)"
R"(        slope = pow(base, exp);
)"
R"(    }
)"
R"(
)"
R"(    // parallel max
)"
R"(    float lmax = -INFINITY;
)"
R"(    for (int i00 = get_local_id(0); i00 < ne00; i00 += get_local_size(0)) {
)"
R"(        lmax = fmax(lmax, psrc0[i00]*scale + (pmask ? slope*pmask[i00] : 0.0f));
)"
R"(    }
)"
R"(    float max = sub_group_reduce_max(lmax);
)"
R"(
)"
R"(    // parallel sum
)"
R"(    float lsum = 0.0f;
)"
R"(    for (int i00 = get_local_id(0); i00 < ne00; i00 += get_local_size(0)) {
)"
R"(        float exp_psrc0 = exp((psrc0[i00]*scale + (pmask ? slope*pmask[i00] : 0.0f)) - max);
)"
R"(        lsum += exp_psrc0;
)"
R"(        // Remember the result of exp here. exp is expensive, so we really do not
)"
R"(        // wish to compute it twice.
)"
R"(        pdst[i00] = exp_psrc0;
)"
R"(    }
)"
R"(
)"
R"(    const float sum = sub_group_reduce_add(lsum);
)"
R"(
)"
R"(    for (int i00 = get_local_id(0); i00 < ne00; i00 += get_local_size(0)) {
)"
R"(        pdst[i00] /= sum;
)"
R"(    }
)"
R"(}
)"
