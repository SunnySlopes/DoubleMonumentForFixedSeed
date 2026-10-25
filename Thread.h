/*
 * Thread pool and result container for the double monument kernel.
 *
 * The include guard and the class names are AIO-specific: the multimonument
 * and dripstonecave kernels compile into the same module and define their own
 * Thread.h-style containers.
 */

#ifndef AIO_DOUBLE_MONUMENT_THREAD_H
#define AIO_DOUBLE_MONUMENT_THREAD_H

#include <atomic>
#include <condition_variable>
#include <functional>
#include <mutex>
#include <queue>
#include <thread>
#include <vector>

template<typename T>
class DoubleResults {
    std::priority_queue<T> results_;
    mutable std::mutex mutex_{};

public:
    void addResult(const T &res)
    {
        std::lock_guard<std::mutex> lock(mutex_);
        results_.emplace(res);
    }

    void clear()
    {
        std::lock_guard<std::mutex> lock(mutex_);
        while (!results_.empty())
            results_.pop();
    }

    bool empty() const
    {
        std::lock_guard<std::mutex> lock(mutex_);
        return results_.empty();
    }

    std::vector<T> peekAll() const
    {
        std::lock_guard<std::mutex> lock(mutex_);
        std::vector<T> copy;
        std::priority_queue<T> temp = results_;
        while (!temp.empty()) {
            copy.push_back(temp.top());
            temp.pop();
        }
        return copy;
    }

    std::vector<T> getAllResults()
    {
        std::lock_guard<std::mutex> lock(mutex_);
        std::vector<T> allResults;
        while (!results_.empty()) {
            allResults.push_back(results_.top());
            results_.pop();
        }
        return allResults;
    }
};

class DoublePool {
    std::vector<std::thread> workers;
    std::queue<std::function<void()>> tasks;
    std::mutex queueMutex;
    std::condition_variable condition;
    std::atomic<bool> stop{false};

public:
    explicit DoublePool(size_t threads)
    {
        for (size_t i = 0; i < threads; ++i) {
            workers.emplace_back([this] {
                while (true) {
                    std::function<void()> task;
                    {
                        std::unique_lock<std::mutex> lock(queueMutex);
                        condition.wait(lock, [this] {
                            return stop || !tasks.empty();
                        });
                        if (stop && tasks.empty())
                            return;
                        task = std::move(tasks.front());
                        tasks.pop();
                    }
                    task();
                }
            });
        }
    }

    template<class F>
    void enqueue(F &&f)
    {
        {
            std::unique_lock<std::mutex> lock(queueMutex);
            tasks.emplace(std::forward<F>(f));
        }
        condition.notify_one();
    }

    ~DoublePool()
    {
        stop = true;
        condition.notify_all();
        for (std::thread &worker : workers) {
            if (worker.joinable())
                worker.join();
        }
    }
};

#endif
