if (redis.call('get', keys[1]) == ARGV[1]) then
    redis.call('del', keys[1]);
end
return 0;