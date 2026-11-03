/*
 * DoubleMonumentForFixedSeed — thin JNI shim.
 *
 * This file contains no search logic: every call is forwarded to the
 * DoubleMonumentFinderBase public C API
 * (DoubleMonumentFinderBase/include/doublemonumentfinder/base.h). The base
 * repository is a git submodule and supplies both the static library linked
 * here and the standalone CLI executable.
 */

#include "generated/project_MonumentFinderBridge.h"

#include "doublemonumentfinder/base.h"

#include <algorithm>
#include <cstdint>
#include <vector>

namespace
{

std::vector<jint> hitsToFlatInts(const std::vector<DmfPairHit> &hits)
{
    std::vector<jint> flat;
    flat.reserve(hits.size() * 5);
    for (const DmfPairHit &h : hits) {
        flat.push_back(static_cast<jint>(h.hang_x));
        flat.push_back(static_cast<jint>(h.hang_z));
        flat.push_back(static_cast<jint>(h.dx));
        flat.push_back(static_cast<jint>(h.dz));
        flat.push_back(static_cast<jint>(h.low_efficiency));
    }
    return flat;
}

jintArray makeIntArray(JNIEnv *env, const std::vector<jint> &data)
{
    if (data.empty())
        return nullptr;

    const jsize len = static_cast<jsize>(data.size());
    jintArray arr = env->NewIntArray(len);
    if (arr == nullptr)
        return nullptr;
    env->SetIntArrayRegion(arr, 0, len, data.data());
    return arr;
}

std::vector<DmfPairHit> snapshotResults()
{
    const int32_t count = dmf_result_count();
    if (count <= 0)
        return {};

    std::vector<DmfPairHit> hits(static_cast<size_t>(count));
    const int32_t copied = dmf_get_results(hits.data(), count);
    if (copied < 0)
        return {};
    hits.resize(static_cast<size_t>(std::min(copied, count)));
    return hits;
}

} // namespace

JNIEXPORT jintArray JNICALL Java_project_MonumentFinderBridge_monumentSearch
  (JNIEnv *env, jclass, jlong seed, jint minX, jint maxX, jint minZ, jint maxZ,
   jint mc, jint numThreads, jboolean pauseOnPairFound)
{
    DmfSearchParams params{};
    params.seed = static_cast<int64_t>(seed);
    params.min_x = minX;
    params.max_x = maxX;
    params.min_z = minZ;
    params.max_z = maxZ;
    params.mc = mc;
    params.threads = std::max<jint>(0, std::min<jint>(numThreads, DMF_MAX_THREADS));
    params.pause_on_pair_found = pauseOnPairFound == JNI_TRUE ? 1 : 0;

    const int rc = dmf_run_search(&params);
    if (rc != DMF_OK)
        return nullptr;

    const int32_t count = dmf_result_count();
    if (count <= 0)
        return nullptr;

    std::vector<DmfPairHit> hits(static_cast<size_t>(count));
    const int32_t copied = dmf_get_results(hits.data(), count);
    if (copied <= 0)
        return nullptr;
    hits.resize(static_cast<size_t>(std::min(copied, count)));

    return makeIntArray(env, hitsToFlatInts(hits));
}

JNIEXPORT jintArray JNICALL Java_project_MonumentFinderBridge_getSearchProgress
  (JNIEnv *env, jclass)
{
    int64_t progress[3] = {0, 0, 0};
    dmf_get_progress(progress);
    if (progress[1] == 0 && progress[2] == 0)
        return nullptr;

    jint data[3] = {
        static_cast<jint>(progress[0]),
        static_cast<jint>(progress[1]),
        static_cast<jint>(progress[2]),
    };
    jintArray arr = env->NewIntArray(3);
    if (arr == nullptr)
        return nullptr;
    env->SetIntArrayRegion(arr, 0, 3, data);
    return arr;
}

JNIEXPORT jintArray JNICALL Java_project_MonumentFinderBridge_getNowResult
  (JNIEnv *env, jclass)
{
    const std::vector<DmfPairHit> hits = snapshotResults();
    if (hits.empty())
        return nullptr;
    return makeIntArray(env, hitsToFlatInts(hits));
}

JNIEXPORT jboolean JNICALL Java_project_MonumentFinderBridge_pause(JNIEnv *, jclass)
{
    return dmf_pause() == DMF_OK ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_project_MonumentFinderBridge_resume(JNIEnv *, jclass)
{
    return dmf_resume() == DMF_OK ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_project_MonumentFinderBridge_stop(JNIEnv *, jclass)
{
    return dmf_stop() == DMF_OK ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_project_MonumentFinderBridge_resetSearchState(JNIEnv *, jclass)
{
    dmf_reset();
}

JNIEXPORT jint JNICALL Java_project_MonumentFinderBridge_classifyOceanType
  (JNIEnv *, jclass, jlong seed, jint mc, jint centerX, jint centerZ)
{
    return dmf_classify_ocean_type(
        static_cast<int64_t>(seed), mc, centerX, centerZ);
}
