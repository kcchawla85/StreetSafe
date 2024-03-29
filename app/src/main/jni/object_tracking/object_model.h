

// NOTE: no native object detectors are currently provided or used by the code
// in this directory. This class remains mainly for historical reasons.
// Detection in the TF demo is done through TensorFlowMultiBoxDetector.java.

// Contains ObjectModelBase declaration.

#ifndef TENSORFLOW_EXAMPLES_ANDROID_JNI_OBJECT_TRACKING_OBJECT_MODEL_H_
#define TENSORFLOW_EXAMPLES_ANDROID_JNI_OBJECT_TRACKING_OBJECT_MODEL_H_

#ifdef __RENDER_OPENGL__
#include <GLES/gl.h>
#include <GLES/glext.h>
#endif

#include <vector>

#include "object_tracking/geom.h"
#include "object_tracking/image-inl.h"
#include "object_tracking/image.h"
#include "object_tracking/integral_image.h"
#ifdef __RENDER_OPENGL__
#include "object_tracking/sprite.h"
#endif
#include "object_tracking/utils.h"

#include "object_tracking/config.h"
#include "object_tracking/image_data.h"
#include "object_tracking/keypoint.h"

namespace tf_tracking {

// The ObjectModelBase class represents all the known appearance information for
// an object. It is not a specific instance of the object in the world,
// but just the general appearance information that enables detection. An
// ObjectModelBase can be reused across multiple-instances of TrackedObjects.
class ObjectModelBase {
 public:
  ObjectModelBase(const std::string& name) : name_(name) {}

  virtual ~ObjectModelBase() {}

  // Called when the next step in an ongoing track occurs.
  virtual void TrackStep(const BoundingBox& position,
                         const Image<uint8_t>& image,
                         const IntegralImage& integral_image,
                         const bool authoritative) {}

  // Called when an object track is lost.
  virtual void TrackLost() {}

  // Called when an object track is confirmed as legitimate.
  virtual void TrackConfirmed() {}

  virtual float GetMaxCorrelation(const Image<float>& patch_image) const = 0;

  virtual MatchScore GetMatchScore(
      const BoundingBox& position, const ImageData& image_data) const = 0;

  virtual void Draw(float* const depth) const = 0;

  inline const std::string& GetName() const {
    return name_;
  }

 protected:
  const std::string name_;

 private:
  TF_DISALLOW_COPY_AND_ASSIGN(ObjectModelBase);
};

template <typename DetectorType>
class ObjectModel : public ObjectModelBase {
 public:
  ObjectModel<DetectorType>(const DetectorType* const detector,
                            const std::string& name)
      : ObjectModelBase(name), detector_(detector) {}

 protected:
  const DetectorType* const detector_;

  TF_DISALLOW_COPY_AND_ASSIGN(ObjectModel<DetectorType>);
};

}  // namespace tf_tracking

#endif  // TENSORFLOW_EXAMPLES_ANDROID_JNI_OBJECT_TRACKING_OBJECT_MODEL_H_
