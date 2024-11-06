import argparse
import open3d as o3d
import open3d.core as o3c

parser = argparse.ArgumentParser()

parser.add_argument('filename')
parser.add_argument('confidence_threshold')

args = parser.parse_args()

f = open(args.filename)

confidence_threshold = float(args.confidence_threshold)

lines = f.readlines()

points = []

for line in lines:
    spl_line = line.split()
    x = float(spl_line[0])
    y = float(spl_line[1])
    z = float(spl_line[2])
    confidence = float(spl_line[3])

    if confidence >= confidence_threshold:
        points.append([x,y,z])

pcd = o3d.t.geometry.PointCloud(o3c.Tensor(points, o3c.float32))

print(pcd)

o3d.visualization.draw_geometries([pcd.to_legacy()],
                                  zoom=0.3412,
                                  front=[0.4257, -0.2125, -0.8795],
                                  lookat=[2.6172, 2.0475, 1.532],
                                  up=[0.0, 1.0, 0.0])

