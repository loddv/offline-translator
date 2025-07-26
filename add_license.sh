#!/bin/bash

LICENSE_HEADER='/*
 * Copyright (C) 2024 David V
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */'

# Find all .kt files and process them
find . -name "*.kt" -type f | while read file; do
    # Check if file already has the license header
    if ! head -n 5 "$file" | grep -q "Copyright (C) 2024 David V"; then
        echo "Adding license to: $file"
        # Create temp file with license header + original content
        {
            echo "$LICENSE_HEADER"
            echo ""
            cat "$file"
        } > "${file}.tmp"
        # Replace original file
        mv "${file}.tmp" "$file"
    else
        echo "License already exists in: $file"
    fi
done