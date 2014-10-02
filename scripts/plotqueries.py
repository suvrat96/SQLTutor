#!/usr/bin/env python
from __future__ import print_function, with_statement
import sys
import matplotlib.pyplot as plt
import numpy
import argparse
import csv
import collections

def create_plot(args):
    data = read_input(args.inputfile)

    counter = collections.Counter(data)
    groups = sorted(counter.keys())
    counts = [counter[g] for g in groups]

    plt.gray()
    plt.bar(groups, counts, align='center', fill=False, hatch='///')
    # plt.subplots_adjust(left=0.05, right=0.95, bottom=0, top=1.1)

    plt.xlabel('# of rules')
    plt.xticks(range(min(groups), max(groups) + 1))
    plt.xlim([min(groups) - 1, max(groups) + 1])
    plt.ylabel('# of query translations')
    plt.yticks(range(min(counts), max(counts) + 1))
    # plt.title('Rule Distribution')
    plt.tight_layout(pad=0.0, rect=(0, 0, 0.925, 1))

    if args.show:
        plt.show()
    else:
        plt.savefig(args.outputfile, format='pdf')


def read_input(inputfile):
    counts = []
    reader = csv.reader(inputfile, delimiter='\t')
    for data in reader:
        if data[0] == 'Source':
            continue
        count = int(data[2])
        counts.append(count)
    return counts


def make_parser():
    p = argparse.ArgumentParser()
    p.add_argument('inputfile', nargs='?', default='-', type=argparse.FileType(mode='r'),
                   help='The input file to read (defaults to standard in)')
    p.add_argument('-o', '--output-file', dest='outputfile', default='queries.pdf',
                   help='The output file to write')
    p.add_argument('-s', '--show', dest='show', default=False, action='store_true',
                   help='Show the figure instead of saving to a file')
    return p


def main(args=None):
    if args is None: args = sys.argv[1:]
    parser = make_parser()
    args = parser.parse_args(args)
    create_plot(args)

if __name__ == '__main__':
    main()
