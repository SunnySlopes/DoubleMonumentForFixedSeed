#include "generated/project_MonumentFinderBridge.h"

#include "../Thread.h"
#include "../monument_search.h"

#include <vector>

static Progress g_progress{};
static ThreadSafeResults<PairHit> g_results{};

static std::vector<jint> pairsToFlatInts(const std::vector<PairHit> &hits)
{
    std::vector<jint> flat;
    flat.reserve(hits.size() * 5);
    for (const PairHit &h : hits) {
        flat.push_back(h.hangX);
        flat.push_back(h.hangZ);
        flat.push_back(h.dx);
        flat.push_back(h.dz);
        flat.push_back(h.lowEfficiency);
    }
    return flat;
}

static jintArray makeIntArray(JNIEnv *env, const std::vector<jint> &data)
{
    if (data.empty())
        return nullptr;
    jsize len = (jsize) data.size();
    jintArray arr = env->NewIntArray(len);
    if (arr == nullptr)
        return nullptr;
    env->SetIntArrayRegion(arr, 0, len, data.data());
    return arr;
}

JNIEXPORT jintArray JNICALL Java_project_MonumentFinderBridge_monumentSearch
  (JNIEnv *env, jclass, jlong seed, jint minX, jint maxX, jint minZ, jint maxZ, jint mc,
   jint numThreads, jboolean pauseOnPairFound) {
    MonumentSearchConfig cfg;
    cfg.seed = (uint64_t) seed;
    cfg.minX = minX;
    cfg.maxX = maxX;
    cfg.minZ = minZ;
    cfg.maxZ = maxZ;
    cfg.mc = mc;
    cfg.pauseOnPairFound = pauseOnPairFound == JNI_TRUE;

    g_results.clear();
    g_progress.current = 0;
    g_progress.total = 0;
    g_progress.status = 0;
    g_progress.try_pause = false;
    g_progress.try_stop = false;

    runMonumentSearch(cfg, &g_progress, g_results, numThreads);

    if (g_progress.try_stop.load())
        return nullptr;

    auto hits = g_results.getAllResults();
    if (hits.empty())
        return nullptr;

    return makeIntArray(env, pairsToFlatInts(hits));
}

JNIEXPORT jintArray JNICALL Java_project_MonumentFinderBridge_getSearchProgress(JNIEnv *env, jclass)
{
    if (g_progress.total.load() == 0 && g_progress.status.load() == 0)
        return nullptr;

    jint data[3] = {
        g_progress.current.load(),
        g_progress.total.load(),
        g_progress.status.load(),
    };
    jintArray arr = env->NewIntArray(3);
    if (arr == nullptr)
        return nullptr;
    env->SetIntArrayRegion(arr, 0, 3, data);
    return arr;
}

JNIEXPORT jintArray JNICALL Java_project_MonumentFinderBridge_getNowResult(JNIEnv *env, jclass)
{
    if (g_results.empty())
        return nullptr;
    auto hits = g_results.peekAll();
    return makeIntArray(env, pairsToFlatInts(hits));
}

JNIEXPORT jboolean JNICALL Java_project_MonumentFinderBridge_pause(JNIEnv *, jclass)
{
    g_progress.try_pause = true;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_project_MonumentFinderBridge_resume(JNIEnv *, jclass)
{
    g_progress.try_pause = false;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_project_MonumentFinderBridge_stop(JNIEnv *, jclass)
{
    g_progress.try_stop = true;
    return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_project_MonumentFinderBridge_resetSearchState(JNIEnv *, jclass)
{
    g_progress.try_pause = false;
    g_progress.try_stop = false;
}
