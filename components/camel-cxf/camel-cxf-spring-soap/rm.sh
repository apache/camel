while read -r filename; do
  rm "$filename"
done <list.txt
