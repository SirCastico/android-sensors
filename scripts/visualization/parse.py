from enum import Enum
from dataclasses import dataclass
import numpy as np

@dataclass
class Intrinsics:
    fx: float
    fy: float
    cx: float
    cy: float

class State(Enum):
    START = 0
    DEPTH_SIZE = 1
    DEPTH = 2
    COLORS = 3
    INTRINSICS = 4
    CAMERA_POSE = 5
    TIMESTAMP = 6
    DEPTH_CONFIDENCE = 7
    COLOR_SIZE = 8

def start_fn(line: str) -> State:
    match line:
        case "depth-size":
            return State.DEPTH_SIZE
        case "color-size":
            return State.COLOR_SIZE
        case "depth":
            return State.DEPTH
        case "colors":
            return State.COLORS
        case "intrinsics":
            return State.INTRINSICS
        case "camera-pose":
            return State.CAMERA_POSE
        case "confidence":
            return State.DEPTH_CONFIDENCE
        case "timestamp":
            return State.TIMESTAMP
        case _:
            return State.START

def depth_size_fn(line: str) -> tuple[tuple[int, int], State]:
    spl = line.split()
    v = (int(spl[0]), int(spl[1]))
    return (v, State.START)

def color_size_fn(line: str) -> tuple[tuple[int, int], State]:
    spl = line.split()
    v = (int(spl[0]), int(spl[1]))
    return (v, State.START)

def depth_fn(line: str) -> tuple[int, State]:
    if line[0].isalpha():
        return (0, start_fn(line))
    else:
        return (int(line), State.DEPTH)

def colors_fn(line: str) -> tuple[tuple[float, float, float], State]:
    if line[0].isalpha():
        return (None, start_fn(line))
    else:
        spl = line.split()
        v = (float(spl[0]), float(spl[1]), float(spl[2]))
        return (v, State.COLORS)

def intrinsics_fn(line: str) -> tuple[Intrinsics, State]:
    spl = line.split()
    intr = Intrinsics(float(spl[0]), float(spl[1]), float(spl[2]), float(spl[3]))
    return (intr, State.START)

def confidence_fn(line: str) -> tuple[float, State]:
    if line[0].isalpha():
        return (None, start_fn(line))
    else:
        return (float(line), State.DEPTH_CONFIDENCE)

def camera_pose_fn(line: str) -> tuple[float, State]:
    if line[0].isalpha():
        return (None, start_fn(line))
    else:
        return (float(line), State.CAMERA_POSE)

def timestamp_fn(line: str) -> tuple[int, State]:
    return (int(line), State.START)

@dataclass
class AppFileData:
    depth: list[int]
    colors: list[tuple[float, float, float]]
    depth_size: tuple[int, int]
    color_size: tuple[int, int]
    confidence: list[float]
    camera_pose: list[float]
    intrinsics: Intrinsics
    timestamp: int

    @staticmethod
    def from_filepath(filepath: str) -> 'AppFileData':
        with open(filepath) as f:
            lines = f.readlines()

        curr_state = State.START
        depth_size = None
        depth = []
        colors = []
        confidence = []
        camera_pose = []
        color_size = None
        intrinsics = None
        timestamp = 0

        for line in lines:
            line = line.rstrip()
            match curr_state:
                case State.START:
                    curr_state = start_fn(line)
                case State.DEPTH_SIZE:
                    depth_size, curr_state = depth_size_fn(line)
               case State.DEPTH_SIZE:
                    color_size, curr_state = color_size_fn(line)
                case State.DEPTH:
                    depth_v, curr_state = depth_fn(line)
                    if curr_state == State.DEPTH:
                        depth.append(depth_v)
                case State.COLORS:
                    color_v, curr_state = colors_fn(line)
                    if curr_state == State.COLORS:
                        colors.append(color_v)
                case State.DEPTH_CONFIDENCE:
                    conf_v, curr_state = confidence_fn(line)
                    if curr_state == State.DEPTH_CONFIDENCE:
                        confidence.append(conf_v)
                case State.CAMERA_POSE:
                    cam_v, curr_state = camera_pose_fn(line)
                    if curr_state == State.CAMERA_POSE:
                        camera_pose.append(cam_v)
                case State.INTRINSICS:
                    intrinsics, curr_state = intrinsics_fn(line)
                case State.TIMESTAMP:
                    timestamp, curr_state = timestamp_fn(line)

        return AppFileData(depth, colors, depth_size, color_size, confidence, camera_pose, intrinsics, timestamp)
