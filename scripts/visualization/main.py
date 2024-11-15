import argparse
from enum import Enum
from dataclasses import dataclass
from parse import AppFileData

import matplotlib.pyplot as plt

def chunks(xs, n):
    n = max(1, n)
    return [xs[i:i+n] for i in range(0, len(xs), n)]


parser = argparse.ArgumentParser()

parser.add_argument('filename')
#parser.add_argument('confidence_threshold')

args = parser.parse_args()

data = AppFileData.from_filepath(args.filename)

depth_bounded = []

low_bound = 0
high_bound = 3000

for v in data.depth:
    if v > high_bound:
        new_v = high_bound
    elif v < low_bound:
        new_v = low_bound
    else:
        new_v = v
    depth_bounded.append(high_bound - new_v + low_bound)
    #depth_bounded.append(new_v)

depth_bounded = chunks(depth_bounded, data.depth_size[0])
colors = chunks(data.colors, data.color_size[0])

#print(len(colors[0]), len(colors))
#print(len(depth[0]), len(depth))

fig = plt.figure(figsize=(10, 10))

fig.add_subplot(1,2,1)
plt.imshow(colors)
fig.add_subplot(1,2,2)
plt.imshow(depth_bounded, cmap='gray', vmin=low_bound, vmax=high_bound)

plt.show()
