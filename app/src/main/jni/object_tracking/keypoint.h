

#ifndef TENSORFLOW_EXAMPLES_ANDROID_JNI_OBJECT_TRACKING_KEYPOINT_H_
#define TENSORFLOW_EXAMPLES_ANDROID_JNI_OBJECT_TRACKING_KEYPOINT_H_

#include "object_tracking/geom.h"
#include "object_tracking/image-inl.h"
#include "object_tracking/image.h"
#include "object_tracking/logging.h"
#include "object_tracking/time_log.h"
#include "object_tracking/utils.h"

#include "object_tracking/config.h"

namespace tf_tracking {

// For keeping track of keypoints.
struct Keypoint {
  Keypoint() : pos_(0.0f, 0.0f), score_(0.0f), type_(0) {}
  Keypoint(const float x, const float y)
      : pos_(x, y), score_(0.0f), type_(0) {}

  Point2f pos_;
  float score_;
  uint8_t type_;
};

inline std::ostream& operator<<(std::ostream& stream, const Keypoint keypoint) {
  return stream << "[" << keypoint.pos_ << ", "
      << keypoint.score_ << ", " << keypoint.type_ << "]";
}

}  // namespace tf_tracking

#endif  // TENSORFLOW_EXAMPLES_ANDROID_JNI_OBJECT_TRACKING_KEYPOINT_H_
