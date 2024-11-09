import mediapipe as mp
import cv2
import numpy as np
import os
from parse import AppFileData  # Updated to import from parse.py
import sys

# Initialize Mediapipe Objectron and drawing utilities
mp_objectron = mp.solutions.objectron
mp_drawing = mp.solutions.drawing_utils

# Define Objectron model names for multiple objects
model_names = ['Chair', 'Cup', 'Camera', 'Shoe']
objectron_models = {
    model_name: mp_objectron.Objectron(
        static_image_mode=False,
        max_num_objects=5,
        min_detection_confidence=0.4,
        min_tracking_confidence=0.7,
        model_name=model_name
    ) for model_name in model_names
}

def load_data(filepath: str) -> AppFileData:
    """
    Load the custom file format and parse it to obtain image and depth data.
    Args:
        filepath (str): Path to the custom format file.
    Returns:
        AppFileData: Parsed data containing depth, color, and other properties.
    """
    return AppFileData.from_filepath(filepath)

def get_depth_map_from_parsed_data(data: AppFileData) -> np.ndarray:
    depth_bounded = []
    low_bound = 0
    high_bound = 3000
    for v in data.depth:
        new_v = max(low_bound, min(v, high_bound))
        depth_bounded.append(new_v)
    depth_map = np.array(depth_bounded).reshape(data.depth_size[1], data.depth_size[0])
    return depth_map

def get_color_image_from_parsed_data(data: AppFileData) -> np.ndarray:
    color_data = np.array(data.colors).reshape(data.depth_size[1], data.depth_size[0], 3)
    color_image = (color_data * 255).astype(np.uint8)
    return color_image

# Get the folder path from command-line arguments
if len(sys.argv) < 2:
    print("Please provide a folder path containing data files.")
    sys.exit(1)

folder_path = sys.argv[1]  # Folder path argument

# Create a single named window and set a fixed size
window_name = 'Object Detection'
cv2.namedWindow(window_name, cv2.WINDOW_NORMAL)
cv2.resizeWindow(window_name, 1280, 720)  # Set a fixed window size

# Iterate over each file in the folder
for filename in os.listdir(folder_path):
    filepath = os.path.join(folder_path, filename)
    if not os.path.isfile(filepath):
        continue  # Skip directories or non-file entries
    print(f"Processing file: {filepath}")
    data = load_data(filepath)
    depth_map = get_depth_map_from_parsed_data(data)
    color_image = get_color_image_from_parsed_data(data)

    image_rgb = cv2.cvtColor(color_image, cv2.COLOR_BGR2RGB)

    # Initialize width, height, and depth
    width = 0.0
    height = 0.0
    depth = 0.0

    dimensionResults = {}

    for model_name, objectron in objectron_models.items():
        results = objectron.process(image_rgb)
        if results.detected_objects:
            for detected_object in results.detected_objects:
                mp_drawing.draw_landmarks(
                    color_image, detected_object.landmarks_2d, mp_objectron.BOX_CONNECTIONS
                )
                mp_drawing.draw_axis(
                    color_image, detected_object.rotation, detected_object.translation
                )

                landmarks_2d = detected_object.landmarks_2d.landmark
                real_world_coords = []

                # Calculate the real-world 3D coordinates for each landmark
                for landmark in landmarks_2d:
                    x = int(landmark.x * color_image.shape[1])
                    y = int(landmark.y * color_image.shape[0])
                    z = depth_map[y, x] / 1000.0  # Convert depth from mm to meters

                    # Add 3D coordinate (x, y, z) in meters
                    real_world_coords.append((landmark.x, landmark.y, z))

                # Calculate the width, height, and depth using the 3D coordinates
                if len(real_world_coords) >= 4:
                    x1, y1, z1 = real_world_coords[0]
                    x2, y2, z2 = real_world_coords[1]
                    x3, y3, z3 = real_world_coords[2]
                    
                    # Width in meters (distance between landmarks 0 and 1)
                    width = np.sqrt((x2 - x1) ** 2 + (y2 - y1) ** 2 + (z2 - z1) ** 2)
                    
                    # Height in meters (distance between landmarks 0 and 2)
                    height = np.sqrt((x3 - x1) ** 2 + (y3 - y1) ** 2 + (z3 - z1) ** 2)
                    
                    # Use average depth
                    depth = np.mean([z1, z2, z3])

                # Store dimensions for this model
                dimensionResults[model_name] = (width, height, depth)

    # Resize the image for display
    resized_image = cv2.resize(color_image, (1280, 720))

    # Draw text on the resized image
    for model_name, dimensions in dimensionResults.items():
        width, height, depth = dimensions
        cv2.putText(
            resized_image, f'{model_name} - Width: {width:.2f}m, Height: {height:.2f}m, Depth: {depth:.2f}m',
            (10, 30 + model_names.index(model_name) * 20),
            cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 0, 0), 2
        )

    # Show the image in the single window
    cv2.imshow(window_name, resized_image)
    if cv2.waitKey(0) & 0xFF == ord('q'):
        break

cv2.destroyAllWindows()
